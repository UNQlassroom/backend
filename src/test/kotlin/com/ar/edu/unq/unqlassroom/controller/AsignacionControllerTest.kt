package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.dto.asignacion.request.*
import com.ar.edu.unq.unqlassroom.dto.asignacion.response.*
import com.ar.edu.unq.unqlassroom.dto.curso.response.RepositorioDTO
import com.ar.edu.unq.unqlassroom.dto.issue.response.IssueResponseDTO
import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.service.AsignacionService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.eq
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class AsignacionControllerTest {

    @Mock
    private lateinit var asignacionService: AsignacionService

    @InjectMocks
    private lateinit var asignacionController: AsignacionController

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    private fun anyAsignacion(): Asignacion {
        any(Asignacion::class.java)
        return Asignacion(
            titulo = "",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = ""
        )
    }

    private fun anyGrupoAsignacion(): GrupoAsignacion {
        any(GrupoAsignacion::class.java)
        return GrupoAsignacion()
    }

    private fun eqString(value: String): String {
        eq(value)
        return value
    }

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(asignacionController).build()
    }

    @Test
    fun `crearAsignacion returns 201 and created assignment`() {
        val request = CrearAsignacionRequestDTO(
            titulo = "TP1 - Recursión",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "template-tp1",
        )

        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 1, comision = 1)
        val asignacionGuardada = Asignacion(
            id = 1L,
            curso = curso,
            titulo = "TP1 - Recursión",
            descripcion = null,
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "template-tp1",
            fechaLimite = null,
        )
        val repo = Repositorio(nombre = "repo1", htmlUrl = "https://github.com/repo1")
        val grupo = GrupoAsignacion(
            id = 100L,
            nombre = null,
            asignacion = asignacionGuardada,
            repositorio = repo,
            integrantes = mutableListOf(Usuario(username = "alumno1")),
        )
        asignacionGuardada.grupos.add(grupo)

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.crearAsignacion(eq(10L), anyAsignacion(), eqString("profe"))).thenReturn(asignacionGuardada)

        mockMvc.perform(
            post("/cursos/10/asignaciones")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.cursoId").value(10))
            .andExpect(jsonPath("$.titulo").value("TP1 - Recursión"))
            .andExpect(jsonPath("$.tipo").value("INDIVIDUAL"))
            .andExpect(jsonPath("$.grupos[0].integrantes[0].username").value("alumno1"))
    }

    @Test
    fun `obtenerAsignaciones returns 200 and list`() {
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 1, comision = 1)
        val asignacion = Asignacion(
            id = 1L,
            curso = curso,
            titulo = "TP1",
            descripcion = null,
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            fechaLimite = null,
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.obtenerAsignaciones(10L, "profe")).thenReturn(listOf(asignacion))

        mockMvc.perform(
            get("/cursos/10/asignaciones")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.size()").value(1))
            .andExpect(jsonPath("$[0].titulo").value("TP1"))
    }

    @Test
    fun `obtenerAsignacion returns 200 and assignment details`() {
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 1, comision = 1)
        val asignacion = Asignacion(
            id = 2L,
            curso = curso,
            titulo = "TP2",
            descripcion = "TP Grupal",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl-grupal",
            fechaLimite = null,
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.obtenerAsignacion(10L, 2L, "profe")).thenReturn(asignacion)

        mockMvc.perform(
            get("/cursos/10/asignaciones/2")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(2))
            .andExpect(jsonPath("$.titulo").value("TP2"))
            .andExpect(jsonPath("$.tipo").value("GRUPAL"))
    }

    @Test
    fun `marcarAsignacionComoEntregada returns 200 and updated assignment`() {
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 1, comision = 1)
        val asignacion = Asignacion(
            id = 5L,
            curso = curso,
            titulo = "TP5",
            descripcion = null,
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl5",
            fechaLimite = null,
        )
        val repo = Repositorio(nombre = "repo5", htmlUrl = "https://github.com/repo5")
        val grupo = GrupoAsignacion(
            id = 50L,
            nombre = null,
            asignacion = asignacion,
            repositorio = repo,
            integrantes = mutableListOf(Usuario(username = "alumno1")),
            entregada = true,
            releaseUrl = "https://github.com/UNQlassroom/repo5/releases/tag/entrega-v1",
        )
        asignacion.grupos.add(grupo)

        val auth = UsernamePasswordAuthenticationToken("alumno1", null)
        `when`(asignacionService.marcarAsignacionComoEntregada(10L, 5L, "alumno1", null)).thenReturn(asignacion)

        mockMvc.perform(
            post("/cursos/10/asignaciones/5/entregar")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.grupos[0].entregada").value(true))
            .andExpect(jsonPath("$.grupos[0].releaseUrl").value("https://github.com/UNQlassroom/repo5/releases/tag/entrega-v1"))
    }

    @Test
    fun `marcarAsignacionComoEntregada with request body specifying grupoId returns 200`() {
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 1, comision = 1)
        val asignacion = Asignacion(
            id = 5L,
            curso = curso,
            titulo = "TP5",
            descripcion = null,
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl5",
            fechaLimite = null,
        )
        val repo = Repositorio(nombre = "repo5", htmlUrl = "https://github.com/repo5")
        val grupo = GrupoAsignacion(
            id = 50L,
            nombre = "Grupo 1",
            asignacion = asignacion,
            repositorio = repo,
            integrantes = mutableListOf(Usuario(username = "alumno1")),
            entregada = true,
        )
        asignacion.grupos.add(grupo)

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.marcarAsignacionComoEntregada(10L, 5L, "profe", 50L)).thenReturn(asignacion)

        mockMvc.perform(
            post("/cursos/10/asignaciones/5/entregar")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(EntregarAsignacionRequestDTO(grupoId = 50L)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.grupos[0].id").value(50))
            .andExpect(jsonPath("$.grupos[0].entregada").value(true))
    }

    @Test
    fun `calificarAsignacion returns 200 and updated assignment with grade and feedback`() {
        val request = CalificarAsignacionRequestDTO(
            grupoId = 50L,
            calificacion = 9,
            observaciones = "Excelente resolución"
        )
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 1, comision = 1)
        val asignacion = Asignacion(
            id = 5L,
            curso = curso,
            titulo = "TP5",
            descripcion = null,
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl5",
            fechaLimite = null,
        )
        val repo = Repositorio(nombre = "repo5", htmlUrl = "https://github.com/repo5")
        val grupo = GrupoAsignacion(
            id = 50L,
            nombre = null,
            asignacion = asignacion,
            repositorio = repo,
            integrantes = mutableListOf(Usuario(username = "alumno1")),
            calificacion = 9,
            observaciones = "Excelente resolución",
        )
        asignacion.grupos.add(grupo)

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.calificarAsignacion(eq(10L), eq(5L), eq(50L), eq(9), eq("Excelente resolución"), eqString("profe"))).thenReturn(asignacion)

        mockMvc.perform(
            post("/cursos/10/asignaciones/5/calificar")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.grupos[0].calificacion").value(9))
            .andExpect(jsonPath("$.grupos[0].observaciones").value("Excelente resolución"))
    }

    @Test
    fun `calificarAsignacion with path grupoId returns 200`() {
        val request = CalificarAsignacionRequestDTO(
            grupoId = 60L,
            calificacion = 8,
            observaciones = "Muy buen trabajo grupal"
        )
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 1, comision = 1)
        val asignacion = Asignacion(
            id = 5L,
            curso = curso,
            titulo = "TP5",
            descripcion = null,
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl5",
            fechaLimite = null,
        )
        val repo = Repositorio(nombre = "repo5", htmlUrl = "https://github.com/repo5")
        val grupo = GrupoAsignacion(
            id = 60L,
            nombre = "Grupo A",
            asignacion = asignacion,
            repositorio = repo,
            integrantes = mutableListOf(Usuario(username = "alumno1"), Usuario(username = "alumno2")),
            calificacion = 8,
            observaciones = "Muy buen trabajo grupal",
        )
        asignacion.grupos.add(grupo)

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.calificarAsignacion(eq(10L), eq(5L), eq(60L), eq(8), eq("Muy buen trabajo grupal"), eqString("profe"))).thenReturn(asignacion)

        mockMvc.perform(
            post("/cursos/10/asignaciones/5/calificar")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.grupos[0].calificacion").value(8))
            .andExpect(jsonPath("$.grupos[0].observaciones").value("Muy buen trabajo grupal"))
    }

    @Test
    fun `calificarAsignacion returns 400 when nota is invalid`() {
        val requestUnderMin = CalificarAsignacionRequestDTO(
            grupoId = 1L,
            calificacion = 0,
            observaciones = "Desaprobado"
        )
        val auth = UsernamePasswordAuthenticationToken("profe", null)

        mockMvc.perform(
            post("/cursos/10/asignaciones/5/calificar")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestUnderMin))
        )
            .andExpect(status().isBadRequest)

        val requestOverMax = CalificarAsignacionRequestDTO(
            grupoId = 1L,
            calificacion = 11,
            observaciones = "Excelente plus"
        )

        mockMvc.perform(
            post("/cursos/10/asignaciones/5/calificar")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestOverMax))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `obtenerCorrecciones returns 200 and list of corrections`() {
        val auth = UsernamePasswordAuthenticationToken("profe", null)
        val issue = IssueResponseDTO(
            numero = 1,
            titulo = "Arreglar tests",
            htmlUrl = "https://github.com/UNQlassroom/repo1/issues/1",
            autor = "profe",
            estado = "PENDIENTE",
            tieneCommitsPosteriores = false,
            cantComentarios = 0,
            fechaCreacion = "2026-09-30T10:00:00Z",
            fechaActualizacion = "2026-09-30T10:00:00Z",
            fechaCierre = null,
        )
        val grupoCorreccion = CorreccionGrupoResponseDTO(
            grupoId = 1L,
            nombre = "alumno1",
            integrantes = listOf("alumno1"),
            repoNombre = "repo1",
            repoHtmlUrl = "https://github.com/UNQlassroom/repo1",
            issues = listOf(issue),
        )

        `when`(asignacionService.obtenerCorrecciones(10L, 5L, "profe"))
            .thenReturn(listOf(grupoCorreccion))

        mockMvc.perform(
            get("/cursos/10/asignaciones/5/correcciones")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].grupoId").value(1))
            .andExpect(jsonPath("$[0].repoNombre").value("repo1"))
            .andExpect(jsonPath("$[0].issues[0].numero").value(1))
            .andExpect(jsonPath("$[0].issues[0].estado").value("PENDIENTE"))
    }
}
