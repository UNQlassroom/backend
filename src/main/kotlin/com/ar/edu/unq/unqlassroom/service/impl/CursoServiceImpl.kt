package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnoTeamMembershipDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.github.GitHubTeamService
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.service.CursoService
import jakarta.transaction.Transactional
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
@Transactional
class CursoServiceImpl (
    private val cursoRepository: CursoRepository,
    private val gitHubTeamService: GitHubTeamService,
) : CursoService {

    override fun crearCurso(dto: CursoRequestDTO): CursoResponseDTO {
        val curso = dto.aModelo()
        val teamResponse = gitHubTeamService.createTeam(
            name = curso.generarNombreTeam(),
            description = curso.generarDescripcionTeam()
            // TODO aca falta pasar como team maintainer al profesor
        )
        curso.githubTeamId = teamResponse.id
        curso.githubTeamSlug = teamResponse.slug

        val cursoGuardado = cursoRepository.save(curso)
        return CursoResponseDTO.desdeModelo(cursoGuardado)
    }

    override fun agregarAlumnos(cursoId: Long, dto: AgregarAlumnosRequestDTO): AgregarAlumnosResponseDTO {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Curso no encontrado con id: $cursoId")
        }

        val teamSlug = curso.githubTeamSlug?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "El curso con id $cursoId no tiene un team de GitHub asociado",
            )

        val distinctUsernames = dto.usernames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val alumnosAgregados = distinctUsernames.map { username ->
            val membership = gitHubTeamService.addMemberToTeam(
                teamSlug = teamSlug,
                username = username,
                role = "member",
            )
            AlumnoTeamMembershipDTO(
                username = username,
                role = membership.role,
                state = membership.state,
            )
        }

        return AgregarAlumnosResponseDTO(
            cursoId = cursoId,
            teamSlug = teamSlug,
            alumnos = alumnosAgregados,
        )
    }
}
