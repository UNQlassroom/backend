package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.github.GitHubTeamService
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.service.CursoService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

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
        )
        curso.githubTeamId = teamResponse.id
        curso.githubTeamSlug = teamResponse.slug

        val cursoGuardado = cursoRepository.save(curso)
        return CursoResponseDTO.desdeModelo(cursoGuardado)
    }
}
