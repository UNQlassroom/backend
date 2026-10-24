package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.dto.asignacion.response.CorreccionGrupoResponseDTO
import com.ar.edu.unq.unqlassroom.dto.issue.response.IssueResponseDTO
import com.ar.edu.unq.unqlassroom.exception.*
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.AsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.repository.IssueRepository
import com.ar.edu.unq.unqlassroom.service.AsignacionService
import com.ar.edu.unq.unqlassroom.service.AsyncService
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
    private val issueRepository: IssueRepository,
    private val asyncService: AsyncService,
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
            throw NoEsDocenteDelCursoException("Solo el docente a cargo del curso puede crear asignaciones")
        }

        if (asignacionRepository.findByCursoIdAndTituloIgnoreCase(cursoId, asignacion.titulo.trim()) != null) {
            throw AsignacionDuplicadaException(asignacion.titulo)
        }

        if (asignacion.estaVencida()) {
            throw FechaLimiteInvalidaException()
        }

        if (!gitHubRepoService.repositoryExists(asignacion.templateRepoName)) {
            throw TemplateRepoNoExisteException(asignacion.templateRepoName)
        }

        asignacion.asociarACurso(curso)
        asignacion.validarEstructuraGrupos()
        asignacion.estado = EstadoAsignacion.CREANDO

        val docenteUsername = curso.owner?.username ?: solicitanteUsername
        val inscripcionesCurso = inscripcionRepository.findByCursoId(cursoId)
        val alumnosInscriptosUsernames = inscripcionesCurso.map { it.usuario.username }.toSet()

        if (asignacion.tipo == TipoAsignacion.INDIVIDUAL) {
            val gruposGenerados = inscripcionesCurso.map { inscripcion ->
                GrupoAsignacion(
                    nombre = null,
                    asignacion = asignacion,
                    repositorio = null,
                    integrantes = mutableListOf(inscripcion.usuario),
                )
            }.toMutableList()
            asignacion.grupos = gruposGenerados
        } else {
            val allMembers = asignacion.grupos.flatMap { it.integrantes.map { u -> u.username.trim() } }
            val notEnrolled = allMembers.filterNot { alumnosInscriptosUsernames.contains(it) }
            if (notEnrolled.isNotEmpty()) {
                throw AlumnosNoInscriptosException(notEnrolled)
            }

            val gruposConfigurados = asignacion.grupos.map { grupoOriginal ->
                val grupoNombre = grupoOriginal.nombre!!.trim()
                val integrantesUsuarios = grupoOriginal.integrantes.map { u ->
                    usuarioService.obtenerOCrearAlumno(u.username.trim())
                }

                GrupoAsignacion(
                    nombre = grupoNombre,
                    asignacion = asignacion,
                    repositorio = null,
                    integrantes = integrantesUsuarios.toMutableList(),
                )
            }
            asignacion.grupos = gruposConfigurados.toMutableList()
        }

        val asignacionGuardada = asignacionRepository.save(asignacion)
        asyncService.crearRepositoriosAsignacionAsincronicamente(asignacionGuardada.id!!, docenteUsername)

        return asignacionGuardada
    }

    override fun modificarAsignacion(
        cursoId: Long,
        asignacionId: Long,
        descripcion: String?,
        fechaLimite: LocalDateTime?,
        solicitanteUsername: String
    ): Asignacion {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        if (!curso.esOwner(solicitanteUsername)) {
            throw NoEsDocenteDelCursoException("Solo el docente a cargo del curso puede modificar asignaciones")
        }

        val asignacion = asignacionRepository.findByIdAndCursoId(asignacionId, cursoId)
            ?: throw AsignacionNotFoundException()

        asignacion.actualizar(descripcion, fechaLimite)

        return asignacionRepository.save(asignacion)
    }

    override fun obtenerAsignaciones(cursoId: Long, solicitanteUsername: String): List<Asignacion> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        val esOwner = curso.esOwner(solicitanteUsername)
        val estaInscripto = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername) != null

        if (!esOwner && !estaInscripto) {
            throw SinPermisoAccesoAsignacionException("No tiene permisos para ver las asignaciones de este curso")
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
            throw SinPermisoAccesoAsignacionException("No tiene permisos para ver esta asignación")
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
            throw SinPermisoEntregaException("No tiene permisos para entregar esta asignación")
        }

        val grupo = if (esOwner) {
            if (grupoId != null) {
                asignacion.buscarGrupo(grupoId)
            } else {
                throw GrupoIdRequeridoParaDocenteException()
            }
        } else {
            val grupoDelAlumno = asignacion.buscarGrupoPorAlumno(solicitanteUsername)
            if (grupoId != null && grupoDelAlumno.id != grupoId) {
                throw SinPermisoEntregaOtroGrupoException()
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
            throw NoEsDocenteDelCursoException("Solo el docente a cargo del curso puede calificar asignaciones")
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
            throw SinPermisoAccesoCorreccionesException()
        }

        val asignacion = asignacionRepository.findByIdAndCursoId(asignacionId, cursoId)
            ?: throw AsignacionNotFoundException()

        val gruposAMostrar = asignacion.paraVisualizacionDe(solicitanteUsername, esOwner).grupos

        return gruposAMostrar.map { grupo ->
            val repo = grupo.repositorio
            val repoNombre = repo?.nombre ?: ""
            val repoHtmlUrl = repo?.htmlUrl ?: ""
            val fechaUltimoCommit = repo?.fechaUltimoCommit

            val repoId = repo?.id
            val issues = if (repoId != null) {
                issueRepository.findByRepositorioIdAndEliminadoFalseOrderByNumeroDesc(repoId)
            } else {
                emptyList()
            }

            val issuesDTOs = issues.map { issue ->
                IssueResponseDTO(
                    numero = issue.numero,
                    titulo = issue.titulo,
                    htmlUrl = issue.htmlUrl,
                    autor = issue.autor,
                    estado = issue.calcularEstado(fechaUltimoCommit),
                    tieneCommitsPosteriores = issue.tieneCommitsPosteriores(fechaUltimoCommit),
                    cantComentarios = issue.cantComentarios,
                    fechaCreacion = issue.fechaCreacion,
                    fechaActualizacion = issue.fechaActualizacion,
                    fechaCierre = issue.fechaCierre,
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

    override fun eliminarAsignacion(
        cursoId: Long,
        asignacionId: Long,
        solicitanteUsername: String
    ) {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        if (!curso.esOwner(solicitanteUsername)) {
            throw NoEsDocenteDelCursoException("Solo el docente a cargo del curso puede eliminar asignaciones")
        }

        val asignacion = asignacionRepository.findByIdAndCursoId(asignacionId, cursoId)
            ?: throw AsignacionNotFoundException()

        asignacion.validarEliminacion()

        val repoNames = asignacion.grupos.mapNotNull { it.repositorio?.nombre }
        val repoIds = asignacion.grupos.mapNotNull { it.repositorio?.id }

        if (repoIds.isNotEmpty()) {
            issueRepository.deleteAllByRepositorioIdIn(repoIds)
        }

        curso.asignaciones.remove(asignacion)
        asignacionRepository.delete(asignacion)

        asyncService.eliminarRepositoriosAsincronicamente(repoNames)
    }
}
