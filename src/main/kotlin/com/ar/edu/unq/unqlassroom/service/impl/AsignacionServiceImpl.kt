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
import java.time.LocalDateTime

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

        if (curso.owner?.username != solicitanteUsername) {
            throw ForbiddenException("Solo el docente a cargo del curso puede crear asignaciones")
        }

        if (!gitHubRepoService.repositoryExists(asignacion.templateRepoName)) {
            throw BadRequestException("El repositorio template '${asignacion.templateRepoName}' no existe en GitHub")
        }

        asignacion.curso = curso

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
                asignacion.grupos.add(grupo)
            }
        } else {
            // GRUPAL
            if (asignacion.grupos.isEmpty()) {
                throw BadRequestException("Para una asignación grupal debe especificar al menos un grupo")
            }

            val allMembers = asignacion.grupos.flatMap { it.integrantes.map { u -> u.username.trim() } }
            if (allMembers.size != allMembers.distinct().size) {
                throw BadRequestException("Un alumno no puede pertenecer a más de un grupo en la misma asignación")
            }

            val notEnrolled = allMembers.filterNot { alumnosInscriptosUsernames.contains(it) }
            if (notEnrolled.isNotEmpty()) {
                throw BadRequestException("Los siguientes alumnos no están inscriptos en el curso: ${notEnrolled.joinToString()}")
            }

            asignacion.grupos.forEach { grupoOriginal ->
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

                val grupo = GrupoAsignacion(
                    nombre = grupoNombre,
                    asignacion = asignacion,
                    repositorio = repositorio,
                    integrantes = integrantesUsuarios.toMutableList(),
                )
                asignacion.grupos.add(grupo)
            }
        }

        return asignacionRepository.save(asignacion)
    }

    override fun obtenerAsignaciones(cursoId: Long, solicitanteUsername: String): List<Asignacion> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val esOwner = curso.owner?.username == solicitanteUsername
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw ForbiddenException("No tiene permisos para ver las asignaciones de este curso")
        }

        val asignaciones = asignacionRepository.findByCursoId(cursoId)

        if (esOwner) {
            return asignaciones
        }

        return asignaciones.map { asignacion ->
            val gruposFiltrados = asignacion.grupos.filter { grupo ->
                grupo.integrantes.any { it.username == solicitanteUsername }
            }
            Asignacion(
                id = asignacion.id,
                titulo = asignacion.titulo,
                descripcion = asignacion.descripcion,
                tipo = asignacion.tipo,
                templateRepoName = asignacion.templateRepoName,
                fechaLimite = asignacion.fechaLimite,
                curso = asignacion.curso,
                grupos = gruposFiltrados.toMutableList()
            )
        }
    }

    override fun obtenerAsignacion(
        cursoId: Long,
        asignacionId: Long,
        solicitanteUsername: String
    ): Asignacion {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val esOwner = curso.owner?.username == solicitanteUsername
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw ForbiddenException("No tiene permisos para ver esta asignación")
        }

        val asignacion = asignacionRepository.findByIdAndCursoId(asignacionId, cursoId)
            ?: throw AsignacionNotFoundException()

        val gruposAMostrar = if (esOwner) {
            asignacion.grupos
        } else {
            asignacion.grupos.filter { grupo ->
                grupo.integrantes.any { it.username == solicitanteUsername }
            }
        }

        gruposAMostrar.forEach { grupo ->
            grupo.repositorio?.let { repo ->
                try {
                    val info = gitHubRepoService.obtenerInformacionRepositorio(repo.nombre)
                    repo.ultimoCommit = info.ultimoCommit
                    repo.fechaUltimoCommit = info.fechaUltimoCommit
                    repo.estadoCI = info.estadoCI
                } catch (_: Exception) {
                    // Si falla consulta puntual a github, mantener el estado persistido
                }
            }
        }

        if (esOwner) {
            return asignacion
        }

        return Asignacion(
            id = asignacion.id,
            titulo = asignacion.titulo,
            descripcion = asignacion.descripcion,
            tipo = asignacion.tipo,
            templateRepoName = asignacion.templateRepoName,
            fechaLimite = asignacion.fechaLimite,
            curso = asignacion.curso,
            grupos = gruposAMostrar.toMutableList()
        )
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

        if (asignacion.fechaLimite != null && LocalDateTime.now().isAfter(asignacion.fechaLimite)) {
            throw BadRequestException("No se puede entregar la asignación porque la fecha límite ha vencido")
        }

        val esOwner = curso.owner?.username == solicitanteUsername
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw ForbiddenException("No tiene permisos para entregar esta asignación")
        }

        val grupo = if (esOwner) {
            if (grupoId != null) {
                asignacion.grupos.find { it.id == grupoId }
                    ?: throw BadRequestException("El grupo especificado no pertenece a la asignación")
            } else {
                throw BadRequestException("Debe especificar el grupoId para marcar la entrega como docente")
            }
        } else {
            val grupoDelAlumno = asignacion.grupos.find { g ->
                g.integrantes.any { it.username == solicitanteUsername }
            } ?: throw BadRequestException("El usuario no pertenece a ningún grupo de esta asignación")

            if (grupoId != null && grupoDelAlumno.id != grupoId) {
                throw ForbiddenException("No tiene permisos para entregar en nombre de otro grupo")
            }
            grupoDelAlumno
        }

        grupo.cantidadEntregas += 1
        grupo.entregada = true
        grupo.fechaEntregada = LocalDateTime.now()

        val tagName = "entrega-v${grupo.cantidadEntregas}"
        val releaseName = "Entrega v${grupo.cantidadEntregas} - ${asignacion.titulo}"
        val releaseBody = "Entrega realizada por $solicitanteUsername el ${grupo.fechaEntregada}"

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
                repo.ultimoCommit = info.ultimoCommit
                repo.fechaUltimoCommit = info.fechaUltimoCommit
                repo.estadoCI = info.estadoCI
            } catch (_: Exception) {
                // Si falla github puntual, continuar
            }
        }

        val guardada = asignacionRepository.save(asignacion)
        if (esOwner) {
            return guardada
        }

        val gruposAMostrar = guardada.grupos.filter { g ->
            g.integrantes.any { it.username == solicitanteUsername }
        }
        return Asignacion(
            id = guardada.id,
            titulo = guardada.titulo,
            descripcion = guardada.descripcion,
            tipo = guardada.tipo,
            templateRepoName = guardada.templateRepoName,
            fechaLimite = guardada.fechaLimite,
            curso = guardada.curso,
            grupos = gruposAMostrar.toMutableList()
        )
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

        if (curso.owner?.username != solicitanteUsername) {
            throw ForbiddenException("Solo el docente a cargo del curso puede calificar asignaciones")
        }

        val asignacion = asignacionRepository.findByIdAndCursoId(asignacionId, cursoId)
            ?: throw AsignacionNotFoundException()

        if (calificacion < 1 || calificacion > 10) {
            throw BadRequestException("La nota debe ser entre 1 y 10")
        }

        val grupo = asignacion.grupos.find { it.id == grupoId }
            ?: throw BadRequestException("El grupo especificado no pertenece a la asignación")

        grupo.calificacion = calificacion
        grupo.observaciones = observaciones
        grupo.fechaCalificacion = LocalDateTime.now()

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

        val esOwner = curso.owner?.username == solicitanteUsername
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw ForbiddenException("No tiene permisos para ver las correcciones de esta asignación")
        }

        val asignacion = asignacionRepository.findByIdAndCursoId(asignacionId, cursoId)
            ?: throw AsignacionNotFoundException()

        val gruposAMostrar = if (esOwner) {
            asignacion.grupos
        } else {
            asignacion.grupos.filter { grupo ->
                grupo.integrantes.any { it.username == solicitanteUsername }
            }
        }

        return gruposAMostrar.map { grupo ->
            grupo.repositorio?.let { repo ->
                try {
                    val info = gitHubRepoService.obtenerInformacionRepositorio(repo.nombre)
                    repo.ultimoCommit = info.ultimoCommit
                    repo.fechaUltimoCommit = info.fechaUltimoCommit
                    repo.estadoCI = info.estadoCI
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
