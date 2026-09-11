package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnoTeamMemberDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnoTeamMembershipDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.ObtenerAlumnosResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.RepositorioDTO
import com.ar.edu.unq.unqlassroom.errors.CursoNotFoundException
import com.ar.edu.unq.unqlassroom.errors.CursoSinGitHubTeamAsociadoException
import com.ar.edu.unq.unqlassroom.github.GitHubRepoService
import com.ar.edu.unq.unqlassroom.github.GitHubTeamService
import com.ar.edu.unq.unqlassroom.model.Alumno
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.repository.AlumnoRepository
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.service.CursoService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
@Transactional
class CursoServiceImpl (
    private val cursoRepository: CursoRepository,
    private val alumnoRepository: AlumnoRepository,
    private val gitHubTeamService: GitHubTeamService,
    private val gitHubRepoService: GitHubRepoService,
) : CursoService {

    override fun crearCurso(dto: CursoRequestDTO): CursoResponseDTO {
        val curso = dto.aModelo()
        val teamResponse = gitHubTeamService.createTeam(
            name = curso.generarNombreTeam(),
            description = curso.generarDescripcionTeam(),
            // TODO aca falta pasar como team maintainer al profesor
        )
        curso.githubTeamId = teamResponse.id
        curso.githubTeamSlug = teamResponse.slug

        val cursoGuardado = cursoRepository.save(curso)
        return CursoResponseDTO.desdeModelo(cursoGuardado)
    }

    override fun obtenerCursos(): List<CursoResponseDTO> {
        return cursoRepository.findAll().map { CursoResponseDTO.desdeModelo(it) }
    }

    override fun agregarAlumnos(cursoId: Long, dto: AgregarAlumnosRequestDTO): AgregarAlumnosResponseDTO {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val teamSlug = curso.githubTeamSlug?.takeIf { it.isNotBlank() }
            ?: throw CursoSinGitHubTeamAsociadoException()

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

            val repositorio = generarRepoParaAlumno(curso, username)

            val alumnoExistente = alumnoRepository.findByCursoIdAndUsername(cursoId, username)
            val alumnoAGuardar = if (alumnoExistente != null) {
                alumnoExistente.role = membership.role
                alumnoExistente.state = membership.state
                alumnoExistente.repositorio = repositorio
                alumnoExistente
            } else {
                Alumno(
                    username = username,
                    role = membership.role,
                    state = membership.state,
                    curso = curso,
                    repositorio = repositorio,
                )
            }
            val alumnoGuardado = alumnoRepository.save(alumnoAGuardar)

            AlumnoTeamMembershipDTO(
                username = alumnoGuardado.username,
                role = alumnoGuardado.role,
                state = alumnoGuardado.state,
                repositorio = alumnoGuardado.repositorio?.let { RepositorioDTO.desdeModelo(it) },
            )
        }

        return AgregarAlumnosResponseDTO(
            cursoId = cursoId,
            teamSlug = teamSlug,
            alumnos = alumnosAgregados,
        )
    }

    private fun generarRepoParaAlumno(curso: Curso, username: String): Repositorio {
        val repoName = gitHubRepoService.generarNombreRepo(curso, username)
        if (!alumnoTieneRepoParaMateria(curso, username, repoName)) {
            gitHubRepoService.createOrgRepository(
                name = repoName,
                description = gitHubRepoService.generarDescripcionRepo(curso, username),
                private = true,
                autoInit = true,
            )
            gitHubRepoService.addCollaborator(
                repoName = repoName,
                username = username,
                permission = "push",
            )
        }

        val info = gitHubRepoService.obtenerInformacionRepositorio(repoName)
        return Repositorio(
            nombre = info.nombre,
            htmlUrl = info.htmlUrl,
            ultimoCommit = info.ultimoCommit,
            fechaUltimoCommit = info.fechaUltimoCommit,
            estadoCI = info.estadoCI,
        )
    }

    private fun alumnoTieneRepoParaMateria(curso: Curso, username: String, repoNameCursoActual: String): Boolean {
        return gitHubRepoService.repositoryExists(repoNameCursoActual)
    }

    override fun obtenerAlumnos(cursoId: Long): ObtenerAlumnosResponseDTO {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val teamSlug = curso.githubTeamSlug?.takeIf { it.isNotBlank() }
            ?: throw CursoSinGitHubTeamAsociadoException()

        val members = gitHubTeamService.getTeamMembers(teamSlug)
        val alumnosPersistidos = alumnoRepository.findByCursoId(cursoId).associateBy { it.username }

        val alumnos = members.map { member ->
            val alumnoPersistido = alumnosPersistidos[member.username]
            val repoName = alumnoPersistido?.repositorio?.nombre
                ?: gitHubRepoService.generarNombreRepo(curso, member.username)

            val repoExiste = alumnoPersistido?.repositorio != null || gitHubRepoService.repositoryExists(repoName)

            val repoDTO = if (repoExiste) {
                val info = gitHubRepoService.obtenerInformacionRepositorio(repoName)
                alumnoPersistido?.repositorio?.apply { // TODO cuando tengamos webhook configurado, tenemos q sincronizar los cambios apenas haya cambios
                    ultimoCommit = info.ultimoCommit
                    fechaUltimoCommit = info.fechaUltimoCommit
                    estadoCI = info.estadoCI
                }
                RepositorioDTO(
                    nombre = info.nombre,
                    htmlUrl = info.htmlUrl,
                    ultimoCommit = info.ultimoCommit,
                    fechaUltimoCommit = info.fechaUltimoCommit,
                    estadoCI = info.estadoCI,
                )
            } else null

            AlumnoTeamMemberDTO(
                username = member.username,
                role = member.role,
                state = member.state,
                repositorio = repoDTO,
            )
        }

        return ObtenerAlumnosResponseDTO(
            cursoId = cursoId,
            teamSlug = teamSlug,
            alumnos = alumnos,
        )
    }
}
