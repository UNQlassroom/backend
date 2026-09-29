package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.controller.dtos.*
import com.ar.edu.unq.unqlassroom.errors.AsignacionNotFoundException
import com.ar.edu.unq.unqlassroom.errors.BadRequestException
import com.ar.edu.unq.unqlassroom.errors.CursoNotFoundException
import com.ar.edu.unq.unqlassroom.errors.ForbiddenException
import com.ar.edu.unq.unqlassroom.github.GitHubCollaboratorService
import com.ar.edu.unq.unqlassroom.github.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.AsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.service.AsignacionService
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
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
) : AsignacionService {

    override fun crearAsignacion(
        cursoId: Long,
        dto: CrearAsignacionRequestDTO,
        solicitanteUsername: String
    ): AsignacionResponseDTO {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        if (curso.owner?.username != solicitanteUsername) {
            throw ForbiddenException("Solo el docente a cargo del curso puede crear asignaciones")
        }

        if (!gitHubRepoService.repositoryExists(dto.templateRepoName)) {
            throw BadRequestException("El repositorio template '${dto.templateRepoName}' no existe en GitHub")
        }

        val docenteUsername = curso.owner?.username ?: solicitanteUsername
        val inscripcionesCurso = inscripcionRepository.findByCursoId(cursoId)
        val alumnosInscriptosUsernames = inscripcionesCurso.map { it.usuario.username }.toSet()

        val asignacion = Asignacion(
            titulo = dto.titulo,
            descripcion = dto.descripcion,
            tipo = dto.tipo,
            templateRepoName = dto.templateRepoName,
            fechaLimite = dto.fechaLimite,
            curso = curso,
        )

        if (dto.tipo == TipoAsignacion.INDIVIDUAL) {
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
            val gruposDTO = dto.grupos
            if (gruposDTO.isNullOrEmpty()) {
                throw BadRequestException("Para una asignación grupal debe especificar al menos un grupo")
            }

            val allMembers = gruposDTO.flatMap { it.integrantesUsernames.map { u -> u.trim() } }
            if (allMembers.size != allMembers.distinct().size) {
                throw BadRequestException("Un alumno no puede pertenecer a más de un grupo en la misma asignación")
            }

            val notEnrolled = allMembers.filterNot { alumnosInscriptosUsernames.contains(it) }
            if (notEnrolled.isNotEmpty()) {
                throw BadRequestException("Los siguientes alumnos no están inscriptos en el curso: ${notEnrolled.joinToString()}")
            }

            gruposDTO.forEach { grupoDTO ->
                val grupoNombre = grupoDTO.nombre.trim()
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

                val integrantesUsuarios = grupoDTO.integrantesUsernames.map { username ->
                    usuarioService.obtenerOCrearAlumno(username.trim())
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

        val asignacionGuardada = asignacionRepository.save(asignacion)
        return AsignacionResponseDTO.desdeModelo(asignacionGuardada)
    }

    override fun obtenerAsignaciones(cursoId: Long, solicitanteUsername: String): List<AsignacionResponseDTO> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val esOwner = curso.owner?.username == solicitanteUsername
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw ForbiddenException("No tiene permisos para ver las asignaciones de este curso")
        }

        val asignaciones = asignacionRepository.findByCursoId(cursoId)

        return asignaciones.map { asignacion ->
            val gruposFiltrados = if (esOwner) {
                asignacion.grupos
            } else {
                asignacion.grupos.filter { grupo ->
                    grupo.integrantes.any { it.username == solicitanteUsername }
                }
            }
            AsignacionResponseDTO.desdeModelo(asignacion, gruposFiltrados)
        }
    }

    override fun obtenerAsignacion(
        cursoId: Long,
        asignacionId: Long,
        solicitanteUsername: String
    ): AsignacionResponseDTO {
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

        // Sincronizar info de repositorio en vivo
        gruposAMostrar.forEach { grupo ->
            try {
                val info = gitHubRepoService.obtenerInformacionRepositorio(grupo.repositorio.nombre)
                grupo.repositorio.ultimoCommit = info.ultimoCommit
                grupo.repositorio.fechaUltimoCommit = info.fechaUltimoCommit
                grupo.repositorio.estadoCI = info.estadoCI
            } catch (_: Exception) {
                // Si falla consulta puntual a github, mantener el estado persistido
            }
        }
        return AsignacionResponseDTO.desdeModelo(asignacion, gruposAMostrar)
    }

    override fun marcarAsignacionComoEntregada(
        cursoId: Long,
        asignacionId: Long,
        solicitanteUsername: String,
        grupoId: Long?
    ): AsignacionResponseDTO {
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

        try {
            val release = gitHubRepoService.createRelease(
                repoName = grupo.repositorio.nombre,
                tagName = tagName,
                name = releaseName,
                body = releaseBody,
            )
            grupo.releaseUrl = release.htmlUrl
        } catch (_: Exception) {
            // Si falla la creación del release en GitHub puntual, continuar registrando la entrega
        }

        try {
            val info = gitHubRepoService.obtenerInformacionRepositorio(grupo.repositorio.nombre)
            grupo.repositorio.ultimoCommit = info.ultimoCommit
            grupo.repositorio.fechaUltimoCommit = info.fechaUltimoCommit
            grupo.repositorio.estadoCI = info.estadoCI
        } catch (_: Exception) {
            // Si falla github puntual, continuar
        }

        val gruposAMostrar = if (esOwner) {
            asignacion.grupos
        } else {
            asignacion.grupos.filter { grupo ->
                grupo.integrantes.any { it.username == solicitanteUsername }
            }
        }

        return AsignacionResponseDTO.desdeModelo(asignacion, gruposAMostrar)
    }

    override fun calificarAsignacion(
        cursoId: Long,
        asignacionId: Long,
        solicitanteUsername: String,
        dto: CalificarAsignacionRequestDTO,
    ): AsignacionResponseDTO {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        if (curso.owner?.username != solicitanteUsername) {
            throw ForbiddenException("Solo el docente a cargo del curso puede calificar asignaciones")
        }

        val asignacion = asignacionRepository.findByIdAndCursoId(asignacionId, cursoId)
            ?: throw AsignacionNotFoundException()

        val calificacion = dto.calificacion ?: throw BadRequestException("La nota debe ser entre 1 y 10")
        if (calificacion < 1 || calificacion > 10) {
            throw BadRequestException("La nota debe ser entre 1 y 10")
        }

        val targetGrupoId = dto.grupoId
        val grupo = if (targetGrupoId != null) {
            asignacion.grupos.find { it.id == targetGrupoId }
                ?: throw BadRequestException("El grupo especificado no pertenece a la asignación")
        } else if (dto.alumnoUsername != null) {
            asignacion.grupos.find { g -> g.integrantes.any { it.username == dto.alumnoUsername } }
                ?: throw BadRequestException("El alumno especificado no pertenece a la asignación")
        } else {
            throw BadRequestException("Debe especificar el grupo a calificar")
        }

        grupo.calificacion = calificacion
        grupo.observaciones = dto.getObservaciones()
        grupo.fechaCalificacion = LocalDateTime.now()

        val asignacionGuardada = asignacionRepository.save(asignacion)

        return AsignacionResponseDTO.desdeModelo(asignacionGuardada)

    }

    override fun crearTemplateRepository(
        dto: CrearTemplateRepoRequestDTO,
        solicitanteUsername: String
    ): TemplateRepoResponseDTO {
        usuarioService.obtenerDocente(solicitanteUsername)

        val repoResponse = gitHubRepoService.createTemplateRepository(
            name = dto.name,
            description = dto.description,
        )

        return TemplateRepoResponseDTO(
            name = repoResponse.name,
            fullName = repoResponse.fullName,
            htmlUrl = repoResponse.htmlUrl,
            description = repoResponse.description,
        )
    }

    override fun listarTemplates(solicitanteUsername: String): List<TemplateRepoResponseDTO> {
        usuarioService.obtenerDocente(solicitanteUsername)

        val templates = gitHubRepoService.listTemplateRepositories()
        return templates.map {
            TemplateRepoResponseDTO(
                name = it.name,
                fullName = it.fullName,
                htmlUrl = it.htmlUrl,
                description = it.description,
            )
        }
    }
}
