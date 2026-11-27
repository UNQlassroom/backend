package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.dto.curso.response.ArchivoExportadoDTO
import com.ar.edu.unq.unqlassroom.exception.*
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubOrgService
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Inscripcion
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.AsyncService
import com.ar.edu.unq.unqlassroom.service.CursoExportService
import com.ar.edu.unq.unqlassroom.service.CursoService
import com.ar.edu.unq.unqlassroom.service.SseNotificationService
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import jakarta.transaction.Transactional
import com.ar.edu.unq.unqlassroom.util.ejecutarDespuesDeCommit
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
@Transactional
class CursoServiceImpl(
    private val cursoRepository: CursoRepository,
    private val usuarioService: UsuarioService,
    private val usuarioRepository: UsuarioRepository,
    private val inscripcionRepository: InscripcionRepository,
    private val gitHubOrgService: GitHubOrgService,
    private val cursoExportService: CursoExportService,
    private val sseNotificationService: SseNotificationService? = null,
    private val asyncService: AsyncService? = null,
) : CursoService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun crearCurso(curso: Curso, creadorUsername: String): Curso {
        val docente = usuarioService.obtenerDocente(creadorUsername)
        if (cursoRepository.existsByMateriaIgnoreCaseAndAnioAndSemestreAndComision(
                curso.materia,
                curso.anio,
                curso.semestre,
                curso.comision
            )
        ) {
            throw CursoDuplicadoException(curso.materia, curso.anio, curso.semestre, curso.comision)
        }
        curso.agregarDocente(docente)
        return cursoRepository.save(curso)
    }

    override fun obtenerCursos(username: String, esDocente: Boolean, archivados: Boolean): List<Curso> {
        return if (esDocente) {
            cursoRepository.findCursosParaDocente(username, archivados)
        } else {
            if (archivados) emptyList() else cursoRepository.findCursosParaAlumno(username)
        }
    }

    override fun obtenerCurso(id: Long, solicitanteUsername: String): Curso {
        val curso = cursoRepository.findById(id).orElseThrow {
            CursoNotFoundException()
        }

        verificarAccesoACurso(curso, solicitanteUsername) {
            SinPermisoAccesoCursoException()
        }

        return curso
    }

    override fun agregarDocentes(
        cursoId: Long,
        usernames: List<String>,
        solicitanteUsername: String
    ): List<Usuario> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        curso.validarEsDocente(solicitanteUsername, "Solo los docentes del curso pueden agregar docentes")
        curso.validarNoArchivado()

        val distinctUsernames = usernames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        val docentesDuplicados = distinctUsernames.filter { rawUsername ->
            curso.esDocente(rawUsername)
        }
        if (docentesDuplicados.isNotEmpty()) {
            throw DocenteDuplicadoException(docentesDuplicados)
        }

        val alumnosInscriptosActivos = inscripcionRepository.findByCursoIdAndActivaTrue(cursoId)
            .map { it.usuario.username.lowercase() }
            .toSet()
        val docentesQueSonAlumnos = distinctUsernames.filter { rawUsername ->
            alumnosInscriptosActivos.contains(rawUsername.lowercase())
        }
        if (docentesQueSonAlumnos.isNotEmpty()) {
            throw DocenteEsAlumnoDelCursoException(docentesQueSonAlumnos)
        }

        val usernamesCanonicos = distinctUsernames.associateWith { gitHubOrgService.obtenerUsernameCanonico(it) }
        val usuariosInexistentes = usernamesCanonicos.filterValues { it == null }.keys.toList()
        if (usuariosInexistentes.isNotEmpty()) {
            throw UsuariosGitHubNoEncontradosException(usuariosInexistentes)
        }

        val docentesAgregados = distinctUsernames.map { rawUsername ->
            val username = usernamesCanonicos.getValue(rawUsername)!!
            val docente = usuarioService.obtenerOCrearDocente(username)

            val membership = gitHubOrgService.invitarMiembro(username, role = "member")
            if (docente.githubState != membership.state) {
                docente.githubState = membership.state
                usuarioRepository.save(docente)
            }

            curso.agregarDocente(docente)
            docente
        }

        cursoRepository.save(curso)

        val docentesUsernames = curso.docentes.map { it.username }
        ejecutarDespuesDeCommit {
            sseNotificationService?.notificarMiembroCursoActualizado(
                cursoId = cursoId,
                docentesUsernames = docentesUsernames,
                alumnosUsernames = emptyList()
            )
        }

        return curso.docentes
    }

    override fun obtenerDocentes(cursoId: Long, solicitanteUsername: String): List<Usuario> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        verificarAccesoACurso(curso, solicitanteUsername) {
            SinPermisoAccesoCursoException("No tiene permisos para ver los docentes de este curso")
        }

        return curso.docentes
    }

    override fun darDeBajaDocente(
        cursoId: Long,
        docenteUsername: String,
        solicitanteUsername: String
    ): Unit {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        curso.validarEsDocente(solicitanteUsername, "Solo los docentes del curso pueden dar de baja docentes")
        curso.validarNoArchivado()

        val normalizedUsername = docenteUsername.trim()
        curso.removerDocente(normalizedUsername)
        cursoRepository.save(curso)

        val docentesUsernames = curso.docentes.map { it.username }
        ejecutarDespuesDeCommit {
            sseNotificationService?.notificarMiembroCursoActualizado(
                cursoId = cursoId,
                docentesUsernames = docentesUsernames,
                alumnosUsernames = listOf(normalizedUsername)
            )
        }
    }

    override fun agregarAlumnos(
        cursoId: Long,
        usernames: List<String>,
        solicitanteUsername: String
    ): List<Inscripcion> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        curso.validarEsDocente(solicitanteUsername, "Solo el docente a cargo del curso puede agregar alumnos")
        curso.validarNoArchivado()

        val distinctUsernames = usernames
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        val alumnosQueSonDocentes = distinctUsernames.filter { rawUsername ->
            curso.esDocente(rawUsername)
        }
        if (alumnosQueSonDocentes.isNotEmpty()) {
            throw AlumnoEsDocenteDelCursoException(alumnosQueSonDocentes)
        }

        val inscripcionesExistentes = inscripcionRepository.findByCursoId(cursoId)
        val inscriptosPorLowercase = inscripcionesExistentes.associateBy { it.usuario.username.lowercase() }

        val alumnosDuplicados = distinctUsernames.mapNotNull { rawUsername ->
            val existente = inscriptosPorLowercase[rawUsername.lowercase()]
            if (existente != null && existente.activa) existente.usuario.username else null
        }.distinct()

        if (alumnosDuplicados.isNotEmpty()) {
            throw AlumnoDuplicadoException(alumnosDuplicados)
        }

        val usernamesCanonicos = distinctUsernames.associateWith { gitHubOrgService.obtenerUsernameCanonico(it) }
        val usuariosInexistentes = usernamesCanonicos.filterValues { it == null }.keys.toList()
        if (usuariosInexistentes.isNotEmpty()) {
            throw UsuariosGitHubNoEncontradosException(usuariosInexistentes)
        }

        val inscripciones = distinctUsernames.map { rawUsername ->
            val username = usernamesCanonicos.getValue(rawUsername)!!
            val usuario = usuarioService.obtenerOCrearAlumno(username)

            val role = if (usuario.githubState == "active") {
                "member"
            } else {
                val membership = gitHubOrgService.invitarMiembro(username)
                usuario.githubState = membership.state
                usuarioRepository.save(usuario)
                membership.role
            }

            val existente = inscriptosPorLowercase[username.lowercase()]
            if (existente != null) {
                existente.reactivar(role)
                inscripcionRepository.save(existente)
            } else {
                val nuevaInscripcion = Inscripcion(
                    curso = curso,
                    usuario = usuario,
                    githubRole = role,
                    activa = true,
                )
                inscripcionRepository.save(nuevaInscripcion)
            }
        }

        val alumnosUsernames = inscripciones.map { it.usuario.username }
        val docentesUsernames = curso.docentes.map { it.username }
        ejecutarDespuesDeCommit {
            sseNotificationService?.notificarMiembroCursoActualizado(
                cursoId = cursoId,
                docentesUsernames = docentesUsernames,
                alumnosUsernames = alumnosUsernames
            )
        }

        return inscripciones
    }

    override fun darDeBajaAlumno(
        cursoId: Long,
        alumnoUsername: String,
        solicitanteUsername: String
    ): Unit {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        curso.validarEsDocente(solicitanteUsername, "Solo el docente a cargo del curso puede dar de baja alumnos")
        curso.validarNoArchivado()

        val normalizedUsername = alumnoUsername.trim()
        val inscripcion = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, normalizedUsername)
        if (inscripcion == null || !inscripcion.activa) {
            throw AlumnoNoInscriptoException(normalizedUsername)
        }

        inscripcion.darDeBaja()
        inscripcionRepository.save(inscripcion)

        val docentesUsernames = curso.docentes.map { it.username }
        ejecutarDespuesDeCommit {
            sseNotificationService?.notificarMiembroCursoActualizado(
                cursoId = cursoId,
                docentesUsernames = docentesUsernames,
                alumnosUsernames = listOf(inscripcion.usuario.username)
            )
        }
    }

    override fun obtenerAlumnos(cursoId: Long, solicitanteUsername: String): List<Inscripcion> {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        verificarAccesoACurso(curso, solicitanteUsername) {
            SinPermisoAccesoCursoException("No tiene permisos para ver los alumnos de este curso")
        }

        return inscripcionRepository.findByCursoIdAndActivaTrue(cursoId)
    }

    override fun archivarCurso(cursoId: Long, solicitanteUsername: String): Curso {
        val curso = cursoRepository.findById(cursoId).orElseThrow {
            CursoNotFoundException()
        }

        curso.validarEsDocente(solicitanteUsername, "Solo el docente a cargo del curso puede archivarlo")

        return ejecutarArchivadoDeCurso(curso)
    }

    override fun exportarCurso(id: Long, solicitanteUsername: String): ArchivoExportadoDTO {
        val curso = cursoRepository.findById(id).orElseThrow {
            CursoNotFoundException()
        }
        curso.validarEsDocente(solicitanteUsername)
        return cursoExportService.exportarPlanillaCurso(curso)
    }

    @Scheduled(cron = "\${cursos.archivado-automatico.cron:0 0 3 * * *}")
    fun ejecutarTareaArchivadoAutomatico() {
        archivarCursosVencidosAutomaticamente(LocalDate.now())
    }

    override fun archivarCursosVencidosAutomaticamente(fechaActual: LocalDate) {
        val cursosAActualizar = cursoRepository.findByArchivadoFalse()
            .filter { it.debeArchivarseAutomaticamente(fechaActual) }

        for (curso in cursosAActualizar) {
            logger.info(
                "Archivando automáticamente curso ID={} ({} - {}s{} c{}) por vencimiento de semestre",
                curso.id,
                curso.materia,
                curso.anio,
                curso.semestre,
                curso.comision
            )
            ejecutarArchivadoDeCurso(curso)
        }
    }

    private fun ejecutarArchivadoDeCurso(curso: Curso): Curso {
        curso.archivar()
        val repoNames = curso.obtenerNombresRepositorios()
        val guardado = cursoRepository.save(curso)

        if (repoNames.isNotEmpty()) {
            ejecutarDespuesDeCommit {
                asyncService?.archivarRepositoriosAsincronicamente(repoNames)
            }
        }

        return guardado
    }

    private fun verificarAccesoACurso(
        curso: Curso,
        solicitanteUsername: String,
        excepcionSiFalla: () -> RuntimeException,
    ): Boolean {
        val cursoId = curso.id ?: throw CursoNotFoundException()
        val esDocente = curso.esDocente(solicitanteUsername)
        val inscripcion = inscripcionRepository.findByCursoIdAndUsuarioUsername(cursoId, solicitanteUsername)
        val estaInscripto = !curso.archivado && inscripcion != null && inscripcion.activa

        if (!esDocente && !estaInscripto) {
            throw excepcionSiFalla()
        }
        return esDocente
    }
}
