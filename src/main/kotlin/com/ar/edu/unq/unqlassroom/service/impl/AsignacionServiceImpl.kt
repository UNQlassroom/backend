package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.dto.asignacion.response.CorreccionGrupoResponseDTO
import com.ar.edu.unq.unqlassroom.dto.issue.response.IssueResponseDTO
import com.ar.edu.unq.unqlassroom.exception.AsignacionNotFoundException
import com.ar.edu.unq.unqlassroom.exception.BadRequestException
import com.ar.edu.unq.unqlassroom.exception.CursoNotFoundException
import com.ar.edu.unq.unqlassroom.exception.ForbiddenException
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubCollaboratorService
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubIssueItemResponse
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubIssueService
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.AsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.service.AsignacionService
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Instant

@Service
@Transactional
class AsignacionServiceImpl(
    private val asignacionRepository: AsignacionRepository,
    private val cursoRepository: CursoRepository,
    private val inscripcionRepository: InscripcionRepository,
    private val usuarioService: UsuarioService,
    private val gitHubRepoService: GitHubRepoService,
    private val gitHubCollaboratorService: GitHubCollaboratorService,
    private val gitHubIssueService: GitHubIssueService,
) : AsignacionService {

    override fun crearAsignacion(
        cursoId: Long,
        asignacion: Asignacion,
        solicitanteUsername: String
    ): Asignacion {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        if (!curso.esOwner(solicitanteUsername)) {
            throw ForbiddenException("Solo el docente a cargo del curso puede crear asignaciones")
        }

        if (asignacionRepository.findByCursoIdAndTituloIgnoreCase(cursoId, asignacion.titulo.trim()) != null) {
            throw BadRequestException("Ya existe una asignación con el título '${asignacion.titulo}' en este curso")
        }

        if (asignacion.estaVencida()) {
            throw BadRequestException("La fecha límite no puede ser anterior a la fecha actual")
        }

        if (!gitHubRepoService.repositoryExists(asignacion.templateRepoName)) {
            throw BadRequestException("El repositorio template '${asignacion.templateRepoName}' no existe en GitHub")
        }

        asignacion.asociarACurso(curso)
        asignacion.validarEstructuraGrupos()

        val docenteUsername = curso.owner?.username ?: solicitanteUsername
        val inscripcionesCurso = inscripcionRepository.findByCursoId(cursoId)
        val alumnosInscriptosUsernames = inscripcionesCurso.map { it.usuario.username }.toSet()

        if (asignacion.tipo == TipoAsignacion.INDIVIDUAL) {
            val gruposGenerados = mutableListOf<GrupoAsignacion>()
            inscripcionesCurso.forEach { inscripcion ->
                val alumno = inscripcion.usuario
                val repoName = asignacion.generarNombreRepo(alumno.username)
                val repoDesc = asignacion.generarDescripcionRepo(alumno.username)

                if (!gitHubRepoService.repositoryExists(repoName)) {
                    gitHubRepoService.createRepositoryFromTemplate(
                        templateRepoName = asignacion.templateRepoName,
                        newRepoName = repoName,
                        description = repoDesc,
                        private = true,
                    )
                }

                gitHubCollaboratorService.addCollaborator(
                    repoName = repoName,
                    username = alumno.username,
                    permission = "push",
                )

                if (docenteUsername != alumno.username) {
                    gitHubCollaboratorService.addCollaborator(
                        repoName = repoName,
                        username = docenteUsername,
                        permission = "push",
                    )
                }

                val info = gitHubRepoService.obtenerInformacionRepositorio(repoName)
                val repositorio = Repositorio(
                    nombre = info.nombre,
                    htmlUrl = info.htmlUrl,
                    ultimoCommit = info.ultimoCommit,
                    fechaUltimoCommit = info.fechaUltimoCommit,
                    estadoCI = info.estadoCI,
                )

                val grupo = GrupoAsignacion(
                    nombre = null,
                    asignacion = asignacion,
                    repositorio = repositorio,
                    integrantes = mutableListOf(alumno),
                )
                gruposGenerados.add(grupo)
            }
            asignacion.grupos = gruposGenerados
        } else {
            // GRUPAL: validar inscripción en el curso
            val allMembers = asignacion.grupos.flatMap { it.integrantes.map { u -> u.username.trim() } }
            val notEnrolled = allMembers.filterNot { alumnosInscriptosUsernames.contains(it) }
            if (notEnrolled.isNotEmpty()) {
                throw BadRequestException("Los siguientes alumnos no están inscriptos en el curso: ${notEnrolled.joinToString()}")
            }

            val gruposConfigurados = asignacion.grupos.map { grupoOriginal ->
                val grupoNombre = grupoOriginal.nombre!!.trim()
                val repoName = asignacion.generarNombreRepo(grupoNombre)
                val repoDesc = asignacion.generarDescripcionRepo(grupoNombre)

                if (!gitHubRepoService.repositoryExists(repoName)) {
                    gitHubRepoService.createRepositoryFromTemplate(
                        templateRepoName = asignacion.templateRepoName,
                        newRepoName = repoName,
                        description = repoDesc,
                        private = true,
                    )
                }

                val integrantesUsuarios = grupoOriginal.integrantes.map { u ->
                    usuarioService.obtenerOCrearAlumno(u.username.trim())
                }

                integrantesUsuarios.forEach { integrante ->
                    gitHubCollaboratorService.addCollaborator(
                        repoName = repoName,
                        username = integrante.username,
                        permission = "push",
                    )
                }

                if (integrantesUsuarios.none { it.username == docenteUsername }) {
                    gitHubCollaboratorService.addCollaborator(
                        repoName = repoName,
                        username = docenteUsername,
                        permission = "push",
                    )
                }

                val info = gitHubRepoService.obtenerInformacionRepositorio(repoName)
                val repositorio = Repositorio(
                    nombre = info.nombre,
                    htmlUrl = info.htmlUrl,
                    ultimoCommit = info.ultimoCommit,
                    fechaUltimoCommit = info.fechaUltimoCommit,
                    estadoCI = info.estadoCI,
                )

                GrupoAsignacion(
                    nombre = grupoNombre,
                    asignacion = asignacion,
                    repositorio = repositorio,
                    integrantes = integrantesUsuarios.toMutableList(),
                )
            }
            asignacion.grupos = gruposConfigurados.toMutableList()
        }

        return asignacionRepository.save(asignacion)
    }

    override fun obtenerAsignaciones(cursoId: Long, solicitanteUsername: String): List<Asignacion> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val esOwner = curso.esOwner(solicitanteUsername)
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw ForbiddenException("No tiene permisos para ver las asignaciones de este curso")
        }

        val asignaciones = asignacionRepository.findByCursoId(cursoId)
        return asignaciones.map { it.paraVisualizacionDe(solicitanteUsername, esOwner) }
    }

    override fun obtenerAsignacion(
        cursoId: Long,
        asignacionId: Long,
        solicitanteUsername: String
    ): Asignacion {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val esOwner = curso.esOwner(solicitanteUsername)
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw ForbiddenException("No tiene permisos para ver esta asignación")
        }

        val asignacion = asignacionRepository.findByIdAndCursoId(asignacionId, cursoId)
            ?: throw AsignacionNotFoundException()

        val asignacionVisible = asignacion.paraVisualizacionDe(solicitanteUsername, esOwner)

        asignacionVisible.grupos.forEach { grupo ->
            grupo.repositorio?.let { repo ->
                try {
                    val info = gitHubRepoService.obtenerInformacionRepositorio(repo.nombre)
                    repo.actualizarInfo(info.ultimoCommit, info.fechaUltimoCommit, info.estadoCI)
                } catch (_: Exception) {
                    // Si falla consulta puntual a github, mantener el estado persistido
                }
            }
        }

        return asignacionVisible
    }

    override fun marcarAsignacionComoEntregada(
        cursoId: Long,
        asignacionId: Long,
        solicitanteUsername: String,
        grupoId: Long?
    ): Asignacion {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val asignacion = asignacionRepository.findByIdAndCursoId(asignacionId, cursoId)
            ?: throw AsignacionNotFoundException()

        asignacion.validarVencimiento()

        val esOwner = curso.esOwner(solicitanteUsername)
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw ForbiddenException("No tiene permisos para entregar esta asignación")
        }

        val grupo = if (esOwner) {
            if (grupoId != null) {
                asignacion.buscarGrupo(grupoId)
            } else {
                throw BadRequestException("Debe especificar el grupoId para marcar la entrega como docente")
            }
        } else {
            val grupoDelAlumno = asignacion.buscarGrupoPorAlumno(solicitanteUsername)
            if (grupoId != null && grupoDelAlumno.id != grupoId) {
                throw ForbiddenException("No tiene permisos para entregar en nombre de otro grupo")
            }
            grupoDelAlumno
        }

        grupo.registrarEntrega()

        val tagName = "entrega-v${grupo.cantidadEntregas}"
        val releaseName = grupo.generarNombreRelease(asignacion.titulo)
        val releaseBody = grupo.generarCuerpoRelease(solicitanteUsername)

        grupo.repositorio?.let { repo ->
            try {
                val release = gitHubRepoService.createRelease(
                    repoName = repo.nombre,
                    tagName = tagName,
                    name = releaseName,
                    body = releaseBody,
                )
                grupo.releaseUrl = release.htmlUrl
            } catch (_: Exception) {
                // Si falla la creación del release en GitHub puntual, continuar registrando la entrega
            }

            try {
                val info = gitHubRepoService.obtenerInformacionRepositorio(repo.nombre)
                repo.actualizarInfo(info.ultimoCommit, info.fechaUltimoCommit, info.estadoCI)
            } catch (_: Exception) {
                // Si falla github puntual, continuar
            }
        }

        val guardada = asignacionRepository.save(asignacion)
        return guardada.paraVisualizacionDe(solicitanteUsername, esOwner)
    }

    override fun calificarAsignacion(
        cursoId: Long,
        asignacionId: Long,
        grupoId: Long,
        calificacion: Int,
        observaciones: String?,
        solicitanteUsername: String,
    ): Asignacion {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        if (!curso.esOwner(solicitanteUsername)) {
            throw ForbiddenException("Solo el docente a cargo del curso puede calificar asignaciones")
        }

        val asignacion = asignacionRepository.findByIdAndCursoId(asignacionId, cursoId)
            ?: throw AsignacionNotFoundException()

        val grupo = asignacion.buscarGrupo(grupoId)
        grupo.calificar(calificacion, observaciones)

        return asignacionRepository.save(asignacion)
    }

    override fun obtenerCorrecciones(
        cursoId: Long,
        asignacionId: Long,
        solicitanteUsername: String
    ): List<CorreccionGrupoResponseDTO> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val esOwner = curso.esOwner(solicitanteUsername)
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw ForbiddenException("No tiene permisos para ver las correcciones de esta asignación")
        }

        val asignacion = asignacionRepository.findByIdAndCursoId(asignacionId, cursoId)
            ?: throw AsignacionNotFoundException()

        val gruposAMostrar = asignacion.paraVisualizacionDe(solicitanteUsername, esOwner).grupos

        return gruposAMostrar.map { grupo ->
            grupo.repositorio?.let { repo ->
                try {
                    val info = gitHubRepoService.obtenerInformacionRepositorio(repo.nombre)
                    repo.actualizarInfo(info.ultimoCommit, info.fechaUltimoCommit, info.estadoCI)
                } catch (_: Exception) {
                    // Mantener estado persistido si falla la consulta
                }
            }

            val repoNombre = grupo.repositorio?.nombre ?: ""
            val repoHtmlUrl = grupo.repositorio?.htmlUrl ?: ""
            val fechaUltimoCommit = grupo.repositorio?.fechaUltimoCommit

            val issuesGitHub = gitHubIssueService.getRepositoryIssues(repoNombre)
            val issuesDTOs = issuesGitHub.map { issue ->
                val (estado, tieneCommitsPosteriores) = calcularEstadoIssue(issue, fechaUltimoCommit)
                IssueResponseDTO(
                    numero = issue.number,
                    titulo = issue.title,
                    htmlUrl = issue.htmlUrl,
                    autor = issue.user?.login ?: "",
                    estado = estado,
                    tieneCommitsPosteriores = tieneCommitsPosteriores,
                    cantComentarios = issue.comments,
                    fechaCreacion = issue.createdAt,
                    fechaActualizacion = issue.updatedAt,
                    fechaCierre = issue.closedAt,
                )
            }

            CorreccionGrupoResponseDTO(
                grupoId = grupo.id!!,
                nombre = grupo.nombre ?: grupo.integrantes.firstOrNull()?.username,
                integrantes = grupo.integrantes.map { it.username },
                repoNombre = repoNombre,
                repoHtmlUrl = repoHtmlUrl,
                issues = issuesDTOs,
            )
        }
    }

    private fun calcularEstadoIssue(
        issue: GitHubIssueItemResponse,
        fechaUltimoCommit: String?
    ): Pair<String, Boolean> {
        if (issue.state.equals("closed", ignoreCase = true)) {
            return Pair("RESUELTO", false)
        }

        val tieneCommitsPosteriores = if (!fechaUltimoCommit.isNullOrBlank() && issue.createdAt.isNotBlank()) {
            try {
                val commitInstant = Instant.parse(fechaUltimoCommit)
                val issueInstant = Instant.parse(issue.createdAt)
                commitInstant.isAfter(issueInstant)
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }

        val estado = if (tieneCommitsPosteriores || issue.comments > 0) {
            "ACTUALIZADO"
        } else {
            "PENDIENTE"
        }

        return Pair(estado, tieneCommitsPosteriores)
    }
}
