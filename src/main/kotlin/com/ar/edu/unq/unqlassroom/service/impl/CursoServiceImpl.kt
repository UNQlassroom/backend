package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnoMiembroDeUnCursoDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnosDeUnCursoResponseDTO
import com.ar.edu.unq.unqlassroom.errors.CursoNotFoundException
import com.ar.edu.unq.unqlassroom.errors.ForbiddenException
import com.ar.edu.unq.unqlassroom.github.GitHubOrgService
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

    override fun crearCurso(dto: CursoRequestDTO, ownerUsername: String): CursoResponseDTO {
        val curso = dto.aModelo()
        curso.owner = usuarioService.obtenerDocente(ownerUsername)

        val cursoGuardado = cursoRepository.save(curso)
        return CursoResponseDTO.desdeModelo(cursoGuardado)
    }

    override fun obtenerCursos(username: String, esDocente: Boolean): List<CursoResponseDTO> {
        val cursos = if (esDocente) {
            cursoRepository.findCursosParaDocente(username)
        } else {
            cursoRepository.findCursosParaAlumno(username)
        }
        return cursos.map { CursoResponseDTO.desdeModelo(it) }
    }

    override fun obtenerCurso(id: Long, solicitanteUsername: String): CursoResponseDTO {
        val curso = cursoRepository.findById(id).orElseThrow {
            CursoNotFoundException()
        }

        val esOwner = curso.owner?.username == solicitanteUsername
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(id, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw ForbiddenException("No tiene permisos para acceder a este curso")
        }

        return CursoResponseDTO.desdeModelo(curso)
    }

    override fun agregarAlumnos(cursoId: Long, dto: AgregarAlumnosRequestDTO, solicitanteUsername: String): AlumnosDeUnCursoResponseDTO {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        if (curso.owner?.username != solicitanteUsername) {
            throw ForbiddenException("Solo el docente a cargo del curso puede agregar alumnos")
        }

        val distinctUsernames = dto.usernames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val alumnosAgregados = distinctUsernames.map { username ->
            val usuario = usuarioService.obtenerOCrearAlumno(username)

            val inscripcionExistente = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, username)
            val inscripcionAGuardar = if (inscripcionExistente != null) {
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

            AlumnoMiembroDeUnCursoDTO(
                username = inscripcionAGuardar.usuario.username,
                role = inscripcionAGuardar.githubRole,
                state = inscripcionAGuardar.githubState,
            )
        }

        return AlumnosDeUnCursoResponseDTO(
            cursoId = cursoId,
            alumnos = alumnosAgregados,
        )
    }

    override fun sincronizarAlumnos(cursoId: Long, solicitanteUsername: String): AlumnosDeUnCursoResponseDTO {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        if (curso.owner?.username != solicitanteUsername) {
            throw ForbiddenException("Solo el docente a cargo del curso puede sincronizar alumnos")
        }

        val inscripciones = inscripcionRepository.findByCursoId(cursoId)

        val alumnosActualizados = inscripciones.map { inscripcion ->
            if (inscripcion.githubState == "pending") {
                val membership = gitHubOrgService.obtenerMembresia(inscripcion.usuario.username)
                if (membership != null && membership.state == "active") {
                    inscripcion.githubState = "active"
                    inscripcionRepository.save(inscripcion)
                }
            }

            AlumnoMiembroDeUnCursoDTO(
                username = inscripcion.usuario.username,
                role = inscripcion.githubRole,
                state = inscripcion.githubState,
            )
        }

        return AlumnosDeUnCursoResponseDTO(
            cursoId = cursoId,
            alumnos = alumnosActualizados,
        )
    }

    override fun obtenerAlumnos(cursoId: Long, solicitanteUsername: String): AlumnosDeUnCursoResponseDTO {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val esOwner = curso.owner?.username == solicitanteUsername
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw ForbiddenException("No tiene permisos para ver los alumnos de este curso")
        }

        val inscripcionesPersistidas = inscripcionRepository.findByCursoId(cursoId)

        val alumnos = inscripcionesPersistidas.map { inscripcion ->
            AlumnoMiembroDeUnCursoDTO(
                username = inscripcion.usuario.username,
                role = inscripcion.githubRole,
                state = inscripcion.githubState,
            )
        }

        return AlumnosDeUnCursoResponseDTO(
            cursoId = cursoId,
            alumnos = alumnos,
        )
    }
}
