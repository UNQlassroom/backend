package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.AsignacionController
import com.ar.edu.unq.unqlassroom.controller.advice.RestExceptionHandler
import com.ar.edu.unq.unqlassroom.dto.asignacion.response.MetricasCIResponseDTO
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
class AsignacionMetricasCITest {

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
    private lateinit var metricaCIRepository: MetricaCIRepository

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
            metricaCIRepository = metricaCIRepository,
        )
        val controller = AsignacionController(asignacionService)
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(RestExceptionHandler())
            .build()
    }

    @Test
    fun `obtenerMetricasCI devuelve las metricas persistidas para el alumno de la asignacion`() {
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

        val metrica = MetricaCI(
            id = 300L,
            commitSha = "sha-123",
            coberturaLineas = 84.5,
            lineasCubiertas = 169,
            lineasPerdidas = 31,
            umbralCobertura = 80.0,
            cumpleUmbralCobertura = true,
            testsEjecutados = 20,
            testsPasados = 19,
            testsFallados = 0,
            testsSalteados = 1,
            autorUsername = "alumno1",
            runId = 700L,
            repositorio = repo,
            grupoAsignacion = grupo,
            asignacion = asignacion,
            alumno = alumno,
        )

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "alumno1"))
            .thenReturn(Inscripcion(id = 1L, curso = curso, usuario = alumno))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(metricaCIRepository.findFirstByRepositorioIdOrderByFechaActualizacionDesc(100L)).thenReturn(metrica)

        val (repoResult, dto) = asignacionService.obtenerMetricasCI(10L, 50L, "alumno1", null)
        assertNotNull(dto)

        assertEquals(300L, dto!!.id)
        assertEquals("repo-alumno1", repoResult.nombre)
        assertEquals("sha-123", dto.commitSha)
        assertEquals(84.5, dto.coberturaLineas)
        assertEquals(169, dto.lineasCubiertas)
        assertEquals(31, dto.lineasPerdidas)
        assertEquals(80.0, dto.umbralCobertura)
        assertTrue(dto.cumpleUmbralCobertura)
        assertEquals(20, dto.testsEjecutados)
        assertEquals(19, dto.testsPasados)
        assertEquals(0, dto.testsFallados)
        assertEquals(1, dto.testsSalteados)
    }

    @Test
    fun `obtenerMetricasCI devuelve resumen vacio sinMetricas cuando aun no se registraron corridas`() {
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

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(metricaCIRepository.findFirstByRepositorioIdOrderByFechaActualizacionDesc(100L)).thenReturn(null)

        val (repoResult, dto) = asignacionService.obtenerMetricasCI(10L, 50L, "profe", 200L)

        assertNull(dto)
        assertEquals("repo-alumno1", repoResult.nombre)
    }

    @Test
    fun `obtenerMetricasCI lanza GrupoIdRequeridoParaDocenteException cuando el docente no envia grupoId`() {
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
            asignacionService.obtenerMetricasCI(10L, 50L, "profe", null)
        }
    }

    @Test
    fun `obtenerMetricasCI lanza SinPermisoAccesoAsignacionException cuando un alumno pide metricas de otro grupo`() {
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
            asignacionService.obtenerMetricasCI(10L, 50L, "alumno1", 999L)
        }
    }

    @Test
    fun `endpoint GET metricas retorna 200 OK con el JSON de MetricasCIResponseDTO`() {
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

        val metrica = MetricaCI(
            id = 301L,
            commitSha = "commit-endpoint",
            coberturaLineas = 91.2,
            lineasCubiertas = 912,
            lineasPerdidas = 88,
            umbralCobertura = 80.0,
            cumpleUmbralCobertura = true,
            testsEjecutados = 30,
            testsPasados = 28,
            testsFallados = 1,
            testsSalteados = 1,
            repositorio = repo,
        )

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "alumno1"))
            .thenReturn(Inscripcion(id = 1L, curso = curso, usuario = alumno))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(metricaCIRepository.findFirstByRepositorioIdOrderByFechaActualizacionDesc(100L)).thenReturn(metrica)

        val auth = UsernamePasswordAuthenticationToken("alumno1", null)

        mockMvc.perform(
            get("/cursos/10/asignaciones/50/metricas")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(301))
            .andExpect(jsonPath("$.repoNombre").value("repo-alumno1"))
            .andExpect(jsonPath("$.commitSha").value("commit-endpoint"))
            .andExpect(jsonPath("$.coberturaLineas").value(91.2))
            .andExpect(jsonPath("$.umbralCobertura").value(80.0))
            .andExpect(jsonPath("$.cumpleUmbralCobertura").value(true))
            .andExpect(jsonPath("$.testsEjecutados").value(30))
            .andExpect(jsonPath("$.testsPasados").value(28))
            .andExpect(jsonPath("$.testsFallados").value(1))
            .andExpect(jsonPath("$.testsSalteados").value(1))
    }

    @Test
    fun `eliminarAsignacion invoca deleteAllByRepositorioIdIn en metricaCIRepository`() {
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
        verify(metricaCIRepository).deleteAllByRepositorioIdIn(listOf(100L))
    }
}

