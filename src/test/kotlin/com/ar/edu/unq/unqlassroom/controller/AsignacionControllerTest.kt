package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.controller.advice.RestExceptionHandler
import com.ar.edu.unq.unqlassroom.dto.asignacion.request.*
import com.ar.edu.unq.unqlassroom.dto.asignacion.response.*
import com.ar.edu.unq.unqlassroom.dto.issue.response.IssueResponseDTO
import com.ar.edu.unq.unqlassroom.exception.AsignacionNotFoundException
import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.service.AsignacionService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.eq
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
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
        .findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    private fun anyAsignacion(): Asignacion {
        any(Asignacion::class.java)
        return Asignacion(
            titulo = "TP Dummy",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl"
        )
    }

    private fun eqString(value: String): String {
        eq(value)
        return value
    }

    private fun <T> anyNullable(clazz: Class<T>): T? {
        org.mockito.Mockito.nullable(clazz)
        return null
    }

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(asignacionController)
            .setControllerAdvice(RestExceptionHandler())
            .build()
    }

    @Test
    fun `crearAsignacion returns 202 Accepted and assignment in CREANDO state`() {
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
            estado = EstadoAsignacion.CREANDO,
        )
        val grupo = GrupoAsignacion(
            id = 100L,
            nombre = null,
            asignacion = asignacionGuardada,
            repositorio = null,
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
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.cursoId").value(10))
            .andExpect(jsonPath("$.titulo").value("TP1 - Recursión"))
            .andExpect(jsonPath("$.tipo").value("INDIVIDUAL"))
            .andExpect(jsonPath("$.estado").value("CREANDO"))
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
            estado = EstadoAsignacion.LISTA,
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
            .andExpect(jsonPath("$[0].estado").value("LISTA"))
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
            estado = EstadoAsignacion.LISTA,
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
            .andExpect(jsonPath("$.estado").value("LISTA"))
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
    fun `marcarAsignacionComoEntregada with query param grupo returns 200`() {
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
            post("/cursos/10/asignaciones/5/entregar?grupo=50")
                .principal(auth)
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
            estado = EstadoIssue.PENDIENTE,
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

    @Test
    fun `eliminarAsignacion returns 202 Accepted`() {
        val auth = UsernamePasswordAuthenticationToken("profe", null)
        doNothing().`when`(asignacionService).eliminarAsignacion(10L, 5L, "profe")

        mockMvc.perform(
            delete("/cursos/10/asignaciones/5")
                .principal(auth)
        )
            .andExpect(status().isAccepted)

        verify(asignacionService).eliminarAsignacion(10L, 5L, "profe")
    }

    @Test
    fun `modificarAsignacion returns 200 OK and updated assignment`() {
        val auth = UsernamePasswordAuthenticationToken("profe", null)
        val request = ModificarAsignacionRequestDTO(
            descripcion = "Descripcion modificada",
            fechaLimite = java.time.LocalDateTime.of(2026, 11, 20, 23, 59, 0),
        )

        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 1, comision = 1)
        val asignacionActualizada = Asignacion(
            id = 5L,
            curso = curso,
            titulo = "TP Original",
            descripcion = "Descripcion modificada",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl-original",
            fechaLimite = java.time.LocalDateTime.of(2026, 11, 20, 23, 59, 0),
        )
        val repo = Repositorio(nombre = "repo1", htmlUrl = "https://github.com/repo1")
        val grupo = GrupoAsignacion(
            id = 100L,
            nombre = null,
            asignacion = asignacionActualizada,
            repositorio = repo,
            integrantes = mutableListOf(Usuario(username = "alumno1")),
        )
        asignacionActualizada.grupos.add(grupo)

        val fechaEsperada = java.time.LocalDateTime.of(2026, 11, 20, 23, 59, 0)
        `when`(
            asignacionService.modificarAsignacion(
                eq(10L),
                eq(5L),
                eqString("Descripcion modificada"),
                eq(fechaEsperada),
                eqString("profe")
            )
        ).thenReturn(asignacionActualizada)

        mockMvc.perform(
            put("/cursos/10/asignaciones/5")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.cursoId").value(10))
            .andExpect(jsonPath("$.titulo").value("TP Original"))
            .andExpect(jsonPath("$.tipo").value("INDIVIDUAL"))
            .andExpect(jsonPath("$.templateRepoName").value("tmpl-original"))
            .andExpect(jsonPath("$.descripcion").value("Descripcion modificada"))

        verify(asignacionService).modificarAsignacion(
            eq(10L),
            eq(5L),
            eqString("Descripcion modificada"),
            eq(fechaEsperada),
            eqString("profe")
        )
    }

    @Test
    fun `modificarAsignacion returns 404 Not Found when assignment does not exist in course`() {
        val auth = UsernamePasswordAuthenticationToken("profe", null)
        val request = ModificarAsignacionRequestDTO(
            descripcion = "Nueva desc",
            fechaLimite = null,
        )

        `when`(
            asignacionService.modificarAsignacion(
                eq(10L),
                eq(999L),
                eqString("Nueva desc"),
                anyNullable(java.time.LocalDateTime::class.java),
                eqString("profe")
            )
        ).thenThrow(AsignacionNotFoundException())

        mockMvc.perform(
            put("/cursos/10/asignaciones/999")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.errorCode").value("ASIGNACION_NOT_FOUND"))
    }
}
