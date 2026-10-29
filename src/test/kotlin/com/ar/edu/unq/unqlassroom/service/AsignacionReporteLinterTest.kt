package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.AsignacionController
import com.ar.edu.unq.unqlassroom.controller.advice.RestExceptionHandler
import com.ar.edu.unq.unqlassroom.exception.*
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.*
import com.ar.edu.unq.unqlassroom.service.impl.AsignacionServiceImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class AsignacionReporteLinterTest {

    @Mock
    private lateinit var asignacionRepository: AsignacionRepository

    @Mock
    private lateinit var cursoRepository: CursoRepository

    @Mock
    private lateinit var inscripcionRepository: InscripcionRepository

    @Mock
    private lateinit var usuarioService: UsuarioService

    @Mock
    private lateinit var gitHubRepoService: GitHubRepoService

    @Mock
    private lateinit var issueRepository: IssueRepository

    @Mock
    private lateinit var asyncService: AsyncService

    @Mock
    private lateinit var reporteLinterRepository: ReporteLinterRepository

    private lateinit var asignacionService: AsignacionServiceImpl
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        asignacionService = AsignacionServiceImpl(
            asignacionRepository = asignacionRepository,
            cursoRepository = cursoRepository,
            inscripcionRepository = inscripcionRepository,
            usuarioService = usuarioService,
            gitHubRepoService = gitHubRepoService,
            issueRepository = issueRepository,
            asyncService = asyncService,
            reporteLinterRepository = reporteLinterRepository,
        )
        val controller = AsignacionController(asignacionService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(RestExceptionHandler())
            .build()
    }

    @Test
    fun `obtenerReporteLinter devuelve el reporte persistido con sus incidencias para el alumno de la asignacion`() {
        val docente = Usuario(id = 1L, username = "profe", esDocente = true)
        val alumno = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val curso = Curso(id = 10L, materia = "IS", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val repo = Repositorio(id = 100L, nombre = "repo-alumno1", htmlUrl = "https://github.com/org/repo-alumno1")
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            estado = EstadoAsignacion.LISTA,
            curso = curso,
        )
        val grupo = GrupoAsignacion(
            id = 200L,
            asignacion = asignacion,
            repositorio = repo,
            integrantes = mutableListOf(alumno),
        )
        asignacion.grupos.add(grupo)

        val reporte = ReporteLinter(
            id = 300L,
            commitSha = "sha-linter-1",
            herramienta = "ktlint",
            cantidadErrores = 1,
            cantidadAdvertencias = 1,
            totalIncidencias = 2,
            autorUsername = "alumno1",
            runId = 700L,
            repositorio = repo,
            grupoAsignacion = grupo,
            asignacion = asignacion,
            alumno = alumno,
        )
        reporte.reemplazarIncidencias(
            listOf(
                IncidenciaLinter(
                    id = 1L,
                    archivo = "src/main/kotlin/App.kt",
                    linea = 3,
                    columna = 1,
                    severidad = SeveridadLinter.ERROR,
                    regla = "unused import",
                    mensaje = "Unused import",
                ),
                IncidenciaLinter(
                    id = 2L,
                    archivo = "src/main/kotlin/App.kt",
                    linea = 20,
                    columna = 5,
                    severidad = SeveridadLinter.WARNING,
                    regla = "MaxLineLength",
                    mensaje = "Line too long",
                ),
            )
        )

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "alumno1"))
            .thenReturn(Inscripcion(id = 1L, curso = curso, usuario = alumno))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(reporteLinterRepository.findFirstByRepositorioIdOrderByFechaActualizacionDesc(100L)).thenReturn(reporte)

        val dto = asignacionService.obtenerReporteLinter(10L, 50L, "alumno1", null)

        assertEquals(300L, dto.id)
        assertEquals("repo-alumno1", dto.repoNombre)
        assertEquals("sha-linter-1", dto.commitSha)
        assertEquals("ktlint", dto.herramienta)
        assertEquals(1, dto.cantidadErrores)
        assertEquals(1, dto.cantidadAdvertencias)
        assertEquals(2, dto.totalIncidencias)
        assertEquals(2, dto.incidencias.size)
        assertEquals("src/main/kotlin/App.kt", dto.incidencias[0].archivo)
        assertEquals(3, dto.incidencias[0].linea)
        assertEquals(SeveridadLinter.ERROR, dto.incidencias[0].severidad)
        assertEquals("unused import", dto.incidencias[0].regla)
    }

    @Test
    fun `obtenerReporteLinter devuelve objeto vacio sinReporte cuando aun no se recibio ningun reporte`() {
        val docente = Usuario(id = 1L, username = "profe", esDocente = true)
        val alumno = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val curso = Curso(id = 10L, materia = "IS", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val repo = Repositorio(id = 100L, nombre = "repo-alumno1", htmlUrl = "https://github.com/org/repo-alumno1")
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )
        val grupo = GrupoAsignacion(
            id = 200L,
            asignacion = asignacion,
            repositorio = repo,
            integrantes = mutableListOf(alumno),
        )
        asignacion.grupos.add(grupo)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "alumno1"))
            .thenReturn(Inscripcion(id = 1L, curso = curso, usuario = alumno))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(reporteLinterRepository.findFirstByRepositorioIdOrderByFechaActualizacionDesc(100L)).thenReturn(null)

        val dto = asignacionService.obtenerReporteLinter(10L, 50L, "alumno1", null)

        assertNull(dto.id)
        assertEquals("repo-alumno1", dto.repoNombre)
        assertEquals(0, dto.cantidadErrores)
        assertEquals(0, dto.cantidadAdvertencias)
        assertEquals(0, dto.totalIncidencias)
        assertTrue(dto.incidencias.isEmpty())
    }

    @Test
    fun `obtenerReporteLinter exige grupoId cuando consulta el docente owner`() {
        val docente = Usuario(id = 1L, username = "profe", esDocente = true)
        val curso = Curso(id = 10L, materia = "IS", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)

        assertThrows<GrupoIdRequeridoParaDocenteException> {
            asignacionService.obtenerReporteLinter(10L, 50L, "profe", null)
        }
    }

    @Test
    fun `obtenerReporteLinter impide que un alumno consulte el reporte de otro grupo`() {
        val docente = Usuario(id = 1L, username = "profe", esDocente = true)
        val alumno1 = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val curso = Curso(id = 10L, materia = "IS", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val repo = Repositorio(id = 100L, nombre = "repo-alumno1", htmlUrl = "https://github.com/org/repo-alumno1")
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )
        val grupo1 = GrupoAsignacion(
            id = 200L,
            asignacion = asignacion,
            repositorio = repo,
            integrantes = mutableListOf(alumno1),
        )
        asignacion.grupos.add(grupo1)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "alumno1"))
            .thenReturn(Inscripcion(id = 1L, curso = curso, usuario = alumno1))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)

        assertThrows<SinPermisoAccesoAsignacionException> {
            asignacionService.obtenerReporteLinter(10L, 50L, "alumno1", 999L)
        }
    }

    @Test
    fun `endpoint GET linter retorna 200 OK con el JSON de ReporteLinterResponseDTO`() {
        val docente = Usuario(id = 1L, username = "profe", esDocente = true)
        val alumno = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val curso = Curso(id = 10L, materia = "IS", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val repo = Repositorio(id = 100L, nombre = "repo-alumno1", htmlUrl = "https://github.com/org/repo-alumno1")
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )
        val grupo = GrupoAsignacion(
            id = 200L,
            asignacion = asignacion,
            repositorio = repo,
            integrantes = mutableListOf(alumno),
        )
        asignacion.grupos.add(grupo)

        val reporte = ReporteLinter(
            id = 301L,
            commitSha = "commit-endpoint",
            herramienta = "detekt",
            cantidadErrores = 1,
            cantidadAdvertencias = 2,
            totalIncidencias = 3,
            repositorio = repo,
        )
        reporte.reemplazarIncidencias(
            listOf(
                IncidenciaLinter(
                    id = 10L,
                    archivo = "src/App.kt",
                    linea = 5,
                    columna = 1,
                    severidad = SeveridadLinter.ERROR,
                    regla = "unused import",
                    mensaje = "Unused import 'java.io.File'",
                )
            )
        )

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "alumno1"))
            .thenReturn(Inscripcion(id = 1L, curso = curso, usuario = alumno))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(reporteLinterRepository.findFirstByRepositorioIdOrderByFechaActualizacionDesc(100L)).thenReturn(reporte)

        val auth = UsernamePasswordAuthenticationToken("alumno1", null)

        mockMvc.perform(
            get("/cursos/10/asignaciones/50/linter")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(301))
            .andExpect(jsonPath("$.repoNombre").value("repo-alumno1"))
            .andExpect(jsonPath("$.commitSha").value("commit-endpoint"))
            .andExpect(jsonPath("$.herramienta").value("detekt"))
            .andExpect(jsonPath("$.cantidadErrores").value(1))
            .andExpect(jsonPath("$.cantidadAdvertencias").value(2))
            .andExpect(jsonPath("$.totalIncidencias").value(3))
            .andExpect(jsonPath("$.incidencias[0].archivo").value("src/App.kt"))
            .andExpect(jsonPath("$.incidencias[0].linea").value(5))
            .andExpect(jsonPath("$.incidencias[0].severidad").value("ERROR"))
            .andExpect(jsonPath("$.incidencias[0].regla").value("unused import"))
    }

    @Test
    fun `eliminarAsignacion invoca deleteAllByRepositorioIdIn en reporteLinterRepository`() {
        val docente = Usuario(id = 1L, username = "profe", esDocente = true)
        val curso = Curso(id = 10L, materia = "IS", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            estado = EstadoAsignacion.LISTA,
            curso = curso,
        )
        val repo = Repositorio(id = 100L, nombre = "repo-alumno1", htmlUrl = "https://github.com/org/repo-alumno1")
        val grupo = GrupoAsignacion(id = 200L, asignacion = asignacion, repositorio = repo)
        asignacion.grupos.add(grupo)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)

        asignacionService.eliminarAsignacion(10L, 50L, "profe")

        verify(issueRepository).deleteAllByRepositorioIdIn(listOf(100L))
        verify(reporteLinterRepository).deleteAllByRepositorioIdIn(listOf(100L))
    }
}

