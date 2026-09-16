package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnoMiembroDeUnCursoDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnosDeUnCursoResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.RepositorioDTO
import com.ar.edu.unq.unqlassroom.errors.CursoNotFoundException
import com.ar.edu.unq.unqlassroom.errors.CursoSinGitHubRepoAsociadoException
import com.ar.edu.unq.unqlassroom.github.GitHubCollaboratorService
import com.ar.edu.unq.unqlassroom.github.GitHubRepoService
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
    private val gitHubRepoService: GitHubRepoService,
    private val gitHubCollaboratorService: GitHubCollaboratorService,
) : CursoService {

    override fun crearCurso(dto: CursoRequestDTO): CursoResponseDTO {
        val curso = dto.aModelo()
        val repoResponse = gitHubRepoService.createOrgRepository(
            name = curso.generarNombreRepo(),
            description = curso.generarDescripcionRepo(),
            private = true,
            autoInit = true,
            // TODO aca falta pasar como team maintainer al profesor
        )
        curso.githubRepoId = repoResponse.id
        curso.githubRepoName = repoResponse.name

        val cursoGuardado = cursoRepository.save(curso)
        return CursoResponseDTO.desdeModelo(cursoGuardado)
    }

    override fun obtenerCursos(): List<CursoResponseDTO> {
        return cursoRepository.findAll().map { CursoResponseDTO.desdeModelo(it) }
    }

    override fun agregarAlumnos(cursoId: Long, dto: AgregarAlumnosRequestDTO): AlumnosDeUnCursoResponseDTO {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val repoName = curso.githubRepoName?.takeIf { it.isNotBlank() }
            ?: throw CursoSinGitHubRepoAsociadoException()

        val distinctUsernames = dto.usernames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val alumnosAgregados = distinctUsernames.map { username ->
            val membership = gitHubCollaboratorService.addCollaborator(
                repoName = repoName,
                username = username,
                permission = "push", // TODO esto deberia ser pull, asi los miembros no tienen write sobre el repo main del curso
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

            AlumnoMiembroDeUnCursoDTO(
                username = alumnoGuardado.username,
                role = alumnoGuardado.role,
                state = alumnoGuardado.state,
                repositorio = alumnoGuardado.repositorio?.let { RepositorioDTO.desdeModelo(it) },
            )
        }

        return AlumnosDeUnCursoResponseDTO(
            cursoId = cursoId,
            repoName = repoName,
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
            gitHubCollaboratorService.addCollaborator(
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

    override fun obtenerAlumnos(cursoId: Long): AlumnosDeUnCursoResponseDTO {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val repoName = curso.githubRepoName?.takeIf { it.isNotBlank() }
            ?: throw CursoSinGitHubRepoAsociadoException()

        val members = gitHubCollaboratorService.getRepoMembers(repoName)
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

            AlumnoMiembroDeUnCursoDTO(
                username = member.username,
                role = member.role,
                state = member.state,
                repositorio = repoDTO,
            )
        }

        return AlumnosDeUnCursoResponseDTO(
            cursoId = cursoId,
            repoName = repoName,
            alumnos = alumnos,
        )
    }
}
