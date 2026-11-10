package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.exception.*
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubOrgService
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Inscripcion
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.AsyncService
import com.ar.edu.unq.unqlassroom.service.CursoService
import com.ar.edu.unq.unqlassroom.service.SseNotificationService
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import jakarta.transaction.Transactional
import com.ar.edu.unq.unqlassroom.util.ejecutarDespuesDeCommit
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
@Transactional
class CursoServiceImpl(
    private val cursoRepository: CursoRepository,
    private val usuarioService: UsuarioService,
    private val usuarioRepository: UsuarioRepository,
    private val inscripcionRepository: InscripcionRepository,
    private val gitHubOrgService: GitHubOrgService,
    private val sseNotificationService: SseNotificationService? = null,
    private val asyncService: AsyncService? = null,
) : CursoService {

    private val logger = LoggerFactory.getLogger(javaClass)

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

    override fun obtenerCursos(username: String, esDocente: Boolean, archivados: Boolean): List<Curso> {
        return if (esDocente) {
            cursoRepository.findCursosParaDocente(username, archivados)
        } else {
            if (archivados) emptyList() else cursoRepository.findCursosParaAlumno(username)
        }
    }

    override fun obtenerCurso(id: Long, solicitanteUsername: String): Curso {
        val curso = cursoRepository.findById(id).orElseThrow {
            CursoNotFoundException()
        }

        verificarAccesoACurso(curso, solicitanteUsername) {
            SinPermisoAccesoCursoException()
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
        curso.validarNoArchivado()

        val distinctUsernames = usernames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        val inscripcionesExistentes = inscripcionRepository.findByCursoId(cursoId)
        val inscriptosPorLowercase = inscripcionesExistentes.associateBy { it.usuario.username.lowercase() }

        val alumnosDuplicados = distinctUsernames.mapNotNull { rawUsername ->
            inscriptosPorLowercase[rawUsername.lowercase()]?.usuario?.username
        }.distinct()

        if (alumnosDuplicados.isNotEmpty()) {
            throw AlumnoDuplicadoException(alumnosDuplicados)
        }

        val usernamesCanonicos = distinctUsernames.associateWith { gitHubOrgService.obtenerUsernameCanonico(it) }
        val usuariosInexistentes = usernamesCanonicos.filterValues { it == null }.keys.toList()
        if (usuariosInexistentes.isNotEmpty()) {
            throw UsuariosGitHubNoEncontradosException(usuariosInexistentes)
        }

        val inscripciones = distinctUsernames.map { rawUsername ->
            val username = usernamesCanonicos.getValue(rawUsername)!!
            val usuario = usuarioService.obtenerOCrearAlumno(username)

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

        val alumnosUsernames = inscripciones.map { it.usuario.username }
        ejecutarDespuesDeCommit {
            sseNotificationService?.notificarMiembroCursoActualizado(
                cursoId = cursoId,
                docenteUsername = curso.owner?.username,
                alumnosUsernames = alumnosUsernames
            )
        }

        return inscripciones
    }

    override fun obtenerAlumnos(cursoId: Long, solicitanteUsername: String): List<Inscripcion> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        verificarAccesoACurso(curso, solicitanteUsername) {
            SinPermisoAccesoCursoException("No tiene permisos para ver los alumnos de este curso")
        }

        return inscripcionRepository.findByCursoId(cursoId)
    }

    override fun archivarCurso(cursoId: Long, solicitanteUsername: String): Curso {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        if (!curso.esOwner(solicitanteUsername)) {
            throw NoEsDocenteDelCursoException("Solo el docente a cargo del curso puede archivarlo")
        }

        return ejecutarArchivadoDeCurso(curso)
    }

    @Scheduled(cron = "\${cursos.archivado-automatico.cron:0 0 3 * * *}")
    fun ejecutarTareaArchivadoAutomatico() {
        archivarCursosVencidosAutomaticamente(LocalDate.now())
    }

    override fun archivarCursosVencidosAutomaticamente(fechaActual: LocalDate) {
        val cursosAActualizar = cursoRepository.findByArchivadoFalse()
            .filter { it.debeArchivarseAutomaticamente(fechaActual) }

        for (curso in cursosAActualizar) {
            logger.info(
                "Archivando automáticamente curso ID={} ({} - {}s{} c{}) por vencimiento de semestre",
                curso.id,
                curso.materia,
                curso.anio,
                curso.semestre,
                curso.comision
            )
            ejecutarArchivadoDeCurso(curso)
        }
    }

    private fun ejecutarArchivadoDeCurso(curso: Curso): Curso {
        curso.archivar()
        val repoNames = curso.obtenerNombresRepositorios()
        val guardado = cursoRepository.save(curso)

        if (repoNames.isNotEmpty()) {
            ejecutarDespuesDeCommit {
                asyncService?.archivarRepositoriosAsincronicamente(repoNames)
            }
        }

        return guardado
    }

    private fun verificarAccesoACurso(
        curso: Curso,
        solicitanteUsername: String,
        excepcionSiFalla: () -> RuntimeException,
    ): Boolean {
        val cursoId = curso.id ?: throw CursoNotFoundException()
        val esOwner = curso.esOwner(solicitanteUsername)
        val estaInscripto = !curso.archivado && inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw excepcionSiFalla()
        }
        return esOwner
    }
}
