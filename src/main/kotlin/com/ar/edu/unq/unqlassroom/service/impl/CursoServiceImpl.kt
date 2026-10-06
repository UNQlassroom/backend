package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.exception.BadRequestException
import com.ar.edu.unq.unqlassroom.exception.CursoNotFoundException
import com.ar.edu.unq.unqlassroom.exception.ForbiddenException
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubOrgService
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Inscripcion
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.service.CursoService
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
@Transactional
class CursoServiceImpl (
    private val cursoRepository: CursoRepository,
    private val usuarioService: UsuarioService,
    private val inscripcionRepository: InscripcionRepository,
    private val gitHubOrgService: GitHubOrgService,
) : CursoService {

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
            throw BadRequestException("Ya existe un curso para la materia '${curso.materia}' en el año ${curso.anio}, semestre ${curso.semestre} y comisión ${curso.comision}")
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
            throw ForbiddenException("No tiene permisos para acceder a este curso")
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
            throw ForbiddenException("Solo el docente a cargo del curso puede agregar alumnos")
        }

        val distinctUsernames = usernames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val usuariosInexistentes = distinctUsernames.filterNot { gitHubOrgService.userExists(it) }
        if (usuariosInexistentes.isNotEmpty()) {
            throw BadRequestException(
                "Los siguientes usuarios no existen en GitHub: ${usuariosInexistentes.joinToString()}"
            )
        }

        return distinctUsernames.map { username ->
            val usuario = usuarioService.obtenerOCrearAlumno(username)

            val inscripcionExistente = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, username)
            if (inscripcionExistente != null) {
                inscripcionExistente
            } else {
                val membership = gitHubOrgService.invitarMiembro(username)
                val nuevaInscripcion = Inscripcion(
                    curso = curso,
                    usuario = usuario,
                    githubRole = membership.role,
                    githubState = membership.state,
                )
                inscripcionRepository.save(nuevaInscripcion)
            }
        }
    }

    override fun sincronizarAlumnos(cursoId: Long, solicitanteUsername: String): List<Inscripcion> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        if (!curso.esOwner(solicitanteUsername)) {
            throw ForbiddenException("Solo el docente a cargo del curso puede sincronizar alumnos")
        }

        val inscripciones = inscripcionRepository.findByCursoId(cursoId)

        return inscripciones.map { inscripcion ->
            val membership = gitHubOrgService.obtenerMembresia(inscripcion.usuario.username)
            if (membership != null && inscripcion.activarSiCorresponde(membership.state)) {
                inscripcionRepository.save(inscripcion)
            }
            inscripcion
        }
    }

    override fun obtenerAlumnos(cursoId: Long, solicitanteUsername: String): List<Inscripcion> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val esOwner = curso.esOwner(solicitanteUsername)
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw ForbiddenException("No tiene permisos para ver los alumnos de este curso")
        }

        return inscripcionRepository.findByCursoId(cursoId)
    }
}
