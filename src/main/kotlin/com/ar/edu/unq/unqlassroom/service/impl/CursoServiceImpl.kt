package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.exception.*
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubOrgService
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Inscripcion
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.CursoService
import com.ar.edu.unq.unqlassroom.service.SseNotificationService
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
@Transactional
class CursoServiceImpl @Autowired constructor(
    private val cursoRepository: CursoRepository,
    private val usuarioService: UsuarioService,
    private val usuarioRepository: UsuarioRepository,
    private val inscripcionRepository: InscripcionRepository,
    private val gitHubOrgService: GitHubOrgService,
    private val sseNotificationService: SseNotificationService? = null,
) : CursoService {

    constructor(
        cursoRepository: CursoRepository,
        usuarioService: UsuarioService,
        usuarioRepository: UsuarioRepository,
        inscripcionRepository: InscripcionRepository,
        gitHubOrgService: GitHubOrgService,
    ) : this(
        cursoRepository = cursoRepository,
        usuarioService = usuarioService,
        usuarioRepository = usuarioRepository,
        inscripcionRepository = inscripcionRepository,
        gitHubOrgService = gitHubOrgService,
        sseNotificationService = null,
    )

    override fun crearCurso(curso: Curso, ownerUsername: String): Curso {
        val docente = usuarioService.obtenerDocente(ownerUsername)
        if (cursoRepository.existsByOwnerUsernameAndMateriaIgnoreCaseAndAnioAndSemestreAndComision(
                docente.username,
                curso.materia,
                curso.anio,
                curso.semestre,
                curso.comision
            )
        ) {
            throw CursoDuplicadoException(curso.materia, curso.anio, curso.semestre, curso.comision)
        }
        curso.asignarOwner(docente)
        return cursoRepository.save(curso)
    }

    override fun obtenerCursos(username: String, esDocente: Boolean): List<Curso> {
        return if (esDocente) {
            cursoRepository.findCursosParaDocente(username)
        } else {
            cursoRepository.findCursosParaAlumno(username)
        }
    }

    override fun obtenerCurso(id: Long, solicitanteUsername: String): Curso {
        val curso = cursoRepository.findById(id).orElseThrow {
            CursoNotFoundException()
        }

        val esOwner = curso.esOwner(solicitanteUsername)
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(id, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw SinPermisoAccesoCursoException()
        }

        return curso
    }

    override fun agregarAlumnos(
        cursoId: Long,
        usernames: List<String>,
        solicitanteUsername: String
    ): List<Inscripcion> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        if (!curso.esOwner(solicitanteUsername)) {
            throw NoEsDocenteDelCursoException("Solo el docente a cargo del curso puede agregar alumnos")
        }

        val distinctUsernames = usernames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        val usernamesCanonicos = distinctUsernames.associateWith { gitHubOrgService.obtenerUsernameCanonico(it) }
        val usuariosInexistentes = usernamesCanonicos.filterValues { it == null }.keys.toList()
        if (usuariosInexistentes.isNotEmpty()) {
            throw UsuariosGitHubNoEncontradosException(usuariosInexistentes)
        }

        val inscripciones = distinctUsernames.map { rawUsername ->
            val username = usernamesCanonicos.getValue(rawUsername)!!
            val usuario = usuarioService.obtenerOCrearAlumno(username)

            val inscripcionExistente = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, username)
            if (inscripcionExistente != null) {
                inscripcionExistente
            } else {
                val role = if (usuario.githubState == "active") {
                    "member"
                } else {
                    val membership = gitHubOrgService.invitarMiembro(username)
                    usuario.githubState = membership.state
                    usuarioRepository.save(usuario)
                    membership.role
                }

                val nuevaInscripcion = Inscripcion(
                    curso = curso,
                    usuario = usuario,
                    githubRole = role,
                )
                inscripcionRepository.save(nuevaInscripcion)
            }
        }

        val alumnosUsernames = inscripciones.map { it.usuario.username }
        try {
            sseNotificationService?.notificarMiembroCursoActualizado(
                cursoId = cursoId,
                docenteUsername = curso.owner?.username,
                alumnosUsernames = alumnosUsernames
            )
        } catch (_: Exception) {
            // Ignorar fallo de notificación para no bloquear la operación
        }

        return inscripciones
    }

    override fun sincronizarAlumnos(cursoId: Long, solicitanteUsername: String): List<Inscripcion> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        if (!curso.esOwner(solicitanteUsername)) {
            throw NoEsDocenteDelCursoException("Solo el docente a cargo del curso puede sincronizar alumnos")
        }

        val inscripciones = inscripcionRepository.findByCursoId(cursoId)
        val activados = mutableListOf<String>()

        val resultado = inscripciones.map { inscripcion ->
            val membership = gitHubOrgService.obtenerMembresia(inscripcion.usuario.username)
            if (membership != null && inscripcion.usuario.activarSiCorresponde(membership.state)) {
                usuarioRepository.save(inscripcion.usuario)
                activados.add(inscripcion.usuario.username)
            }
            inscripcion
        }

        if (activados.isNotEmpty()) {
            try {
                sseNotificationService?.notificarMiembroCursoActualizado(
                    cursoId = cursoId,
                    docenteUsername = curso.owner?.username,
                    alumnosUsernames = activados
                )
            } catch (_: Exception) {
                // Ignorar fallo de notificación
            }
        }

        return resultado
    }

    override fun obtenerAlumnos(cursoId: Long, solicitanteUsername: String): List<Inscripcion> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val esOwner = curso.esOwner(solicitanteUsername)
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw SinPermisoAccesoCursoException("No tiene permisos para ver los alumnos de este curso")
        }

        return inscripcionRepository.findByCursoId(cursoId)
    }
}
