package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnoMiembroDeUnCursoDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnosDeUnCursoResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.RepositorioDTO
import com.ar.edu.unq.unqlassroom.errors.CursoNotFoundException
import com.ar.edu.unq.unqlassroom.errors.CursoSinGitHubRepoAsociadoException
import com.ar.edu.unq.unqlassroom.errors.ForbiddenException
import com.ar.edu.unq.unqlassroom.github.GitHubCollaboratorService
import com.ar.edu.unq.unqlassroom.github.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Inscripcion
import com.ar.edu.unq.unqlassroom.model.Repositorio
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
    private val gitHubRepoService: GitHubRepoService,
    private val gitHubCollaboratorService: GitHubCollaboratorService,
) : CursoService {

    override fun crearCurso(dto: CursoRequestDTO, ownerUsername: String): CursoResponseDTO {
        val curso = dto.aModelo()
        curso.owner = usuarioService.obtenerDocente(ownerUsername)

        val repoResponse = gitHubRepoService.createOrgRepository(
            name = curso.generarNombreRepo(),
            description = curso.generarDescripcionRepo(),
            private = true,
            autoInit = true,
            // TODO aca falta pasar como team maintainer al profesor
        )
        curso.githubRepoId = repoResponse.id
        curso.githubRepoName = repoResponse.name

        gitHubCollaboratorService.addCollaborator(
            repoName = curso.githubRepoName!!,
            username = ownerUsername,
            permission = "push",
        )

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

    override fun agregarAlumnos(cursoId: Long, dto: AgregarAlumnosRequestDTO, solicitanteUsername: String): AlumnosDeUnCursoResponseDTO {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        if (curso.owner?.username != solicitanteUsername) {
            throw ForbiddenException("Solo el docente a cargo del curso puede agregar alumnos")
        }

        val repoName = curso.githubRepoName?.takeIf { it.isNotBlank() }
            ?: throw CursoSinGitHubRepoAsociadoException()

        val distinctUsernames = dto.usernames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val alumnosAgregados = distinctUsernames.map { username ->
            val collaborator = gitHubCollaboratorService.addCollaborator(
                repoName = repoName,
                username = username,
                permission = "push", // TODO esto deberia ser pull, asi los miembros no tienen write sobre el repo main del curso
            )

            val repositorio = generarRepoParaAlumno(curso, username)

            val usuario = usuarioService.obtenerOCrearAlumno(username)

            val inscripcionExistente = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, username)
            val inscripcionAGuardar = if (inscripcionExistente != null) {
                inscripcionExistente.githubRole = collaborator.role
                inscripcionExistente.githubState = collaborator.state
                inscripcionExistente.repositorio = repositorio
                inscripcionExistente
            } else {
                Inscripcion(
                    curso = curso,
                    usuario = usuario,
                    githubRole = collaborator.role,
                    githubState = collaborator.state,
                    repositorio = repositorio,
                )
            }
            val inscripcionGuardada = inscripcionRepository.save(inscripcionAGuardar)

            AlumnoMiembroDeUnCursoDTO(
                username = inscripcionGuardada.usuario.username,
                role = inscripcionGuardada.githubRole,
                state = inscripcionGuardada.githubState,
                repositorio = inscripcionGuardada.repositorio?.let { RepositorioDTO.desdeModelo(it) },
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

    override fun obtenerAlumnos(cursoId: Long, solicitanteUsername: String): AlumnosDeUnCursoResponseDTO {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val esOwner = curso.owner?.username == solicitanteUsername
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw ForbiddenException("No tiene permisos para ver los alumnos de este curso")
        }

        val repoName = curso.githubRepoName?.takeIf { it.isNotBlank() }
            ?: throw CursoSinGitHubRepoAsociadoException()

        val members = gitHubCollaboratorService.getRepoMembers(repoName)
        val inscripcionesPersistidas = inscripcionRepository.findByCursoId(cursoId).associateBy { it.usuario.username }

        val alumnos = members.map { member ->
            val inscripcionPersistida = inscripcionesPersistidas[member.username]
            val repoName = inscripcionPersistida?.repositorio?.nombre
                ?: gitHubRepoService.generarNombreRepo(curso, member.username)

            val repoExiste = inscripcionPersistida?.repositorio != null || gitHubRepoService.repositoryExists(repoName)

            val repoDTO = if (repoExiste) {
                val info = gitHubRepoService.obtenerInformacionRepositorio(repoName)
                inscripcionPersistida?.repositorio?.apply { // TODO cuando tengamos webhook configurado, tenemos q sincronizar los cambios apenas haya cambios
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
