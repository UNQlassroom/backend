package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.dto.asignacion.*
import com.ar.edu.unq.unqlassroom.dto.curso.RepositorioDTO
import com.ar.edu.unq.unqlassroom.dto.issue.IssueResponseDTO
import com.ar.edu.unq.unqlassroom.model.TipoAsignacion
import com.ar.edu.unq.unqlassroom.service.AsignacionService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
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

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(asignacionController).build()
    }

    @Test
    fun `crearAsignacion returns 201 and created assignment`() {
        val request = CrearAsignacionRequestDTO(
            titulo = "TP1 - RecursiÃ³n",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "template-tp1",
        )

        val response = AsignacionResponseDTO(
            id = 1L,
            cursoId = 10L,
            titulo = "TP1 - RecursiÃ³n",
            descripcion = null,
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "template-tp1",
            fechaLimite = null,
            grupos = listOf(
                GrupoAsignacionResponseDTO(
                    id = 100L,
                    nombre = null,
                    integrantes = listOf("alumno1"),
                    repositorio = RepositorioDTO("repo1", "https://github.com/repo1", null, null, null)
                )
            )
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.crearAsignacion(10L, request, "profe")).thenReturn(response)

        mockMvc.perform(
            post("/cursos/10/asignaciones")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.cursoId").value(10))
            .andExpect(jsonPath("$.titulo").value("TP1 - RecursiÃ³n"))
            .andExpect(jsonPath("$.tipo").value("INDIVIDUAL"))
            .andExpect(jsonPath("$.grupos[0].integrantes[0]").value("alumno1"))
    }

    @Test
    fun `obtenerAsignaciones returns 200 and list`() {
        val response = listOf(
            AsignacionResponseDTO(
                id = 1L,
                cursoId = 10L,
                titulo = "TP1",
                descripcion = null,
                tipo = TipoAsignacion.INDIVIDUAL,
                templateRepoName = "tmpl",
                fechaLimite = null,
                grupos = emptyList()
            )
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.obtenerAsignaciones(10L, "profe")).thenReturn(response)

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
        val response = AsignacionResponseDTO(
            id = 2L,
            cursoId = 10L,
            titulo = "TP2",
            descripcion = "TP Grupal",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl-grupal",
            fechaLimite = null,
            grupos = emptyList()
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.obtenerAsignacion(10L, 2L, "profe")).thenReturn(response)

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
    fun `crearTemplate returns 201 and created template`() {
        val request = CrearTemplateRepoRequestDTO("template-tp3", "Desc")
        val response = TemplateRepoResponseDTO("template-tp3", "org/template-tp3", "https://github.com/org/template-tp3", "Desc")

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.crearTemplateRepository(request, "profe")).thenReturn(response)

        mockMvc.perform(
            post("/templates")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("template-tp3"))
            .andExpect(jsonPath("$.htmlUrl").value("https://github.com/org/template-tp3"))
    }

    @Test
    fun `listarTemplates returns 200 and list`() {
        val response = listOf(
            TemplateRepoResponseDTO("template-tp3", "org/template-tp3", "https://github.com/org/template-tp3", "Desc")
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.listarTemplates("profe")).thenReturn(response)

        mockMvc.perform(
            get("/templates")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.size()").value(1))
            .andExpect(jsonPath("$[0].name").value("template-tp3"))
    }

    @Test
    fun `marcarAsignacionComoEntregada returns 200 and updated assignment`() {
        val response = AsignacionResponseDTO(
            id = 5L,
            cursoId = 10L,
            titulo = "TP5",
            descripcion = null,
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl5",
            fechaLimite = null,
            grupos = listOf(
                GrupoAsignacionResponseDTO(
                    id = 50L,
                    nombre = null,
                    integrantes = listOf("alumno1"),
                    repositorio = RepositorioDTO("repo5", "https://github.com/repo5", null, null, null),
                    entregada = true,
                    releaseUrl = "https://github.com/UNQlassroom/repo5/releases/tag/entrega-v1",
                )
            ),
        )

        val auth = UsernamePasswordAuthenticationToken("alumno1", null)
        `when`(asignacionService.marcarAsignacionComoEntregada(10L, 5L, "alumno1", null)).thenReturn(response)

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
        val response = AsignacionResponseDTO(
            id = 5L,
            cursoId = 10L,
            titulo = "TP5",
            descripcion = null,
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl5",
            fechaLimite = null,
            grupos = listOf(
                GrupoAsignacionResponseDTO(
                    id = 50L,
                    nombre = "Grupo 1",
                    integrantes = listOf("alumno1"),
                    repositorio = RepositorioDTO("repo5", "https://github.com/repo5", null, null, null),
                    entregada = true,
                )
            ),
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.marcarAsignacionComoEntregada(10L, 5L, "profe", 50L)).thenReturn(response)

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
            observaciones = "Excelente resoluciÃ³n"
        )
        val response = AsignacionResponseDTO(
            id = 5L,
            cursoId = 10L,
            titulo = "TP5",
            descripcion = null,
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl5",
            fechaLimite = null,
            grupos = listOf(
                GrupoAsignacionResponseDTO(
                    id = 50L,
                    nombre = null,
                    integrantes = listOf("alumno1"),
                    repositorio = RepositorioDTO("repo5", "https://github.com/repo5", null, null, null),
                    calificacion = 9,
                    observaciones = "Excelente resoluciÃ³n",
                )
            ),
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.calificarAsignacion(10L, 5L, "profe", request)).thenReturn(response)

        mockMvc.perform(
            post("/cursos/10/asignaciones/5/individual/calificar")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(5))
            .andExpect(jsonPath("$.grupos[0].calificacion").value(9))
            .andExpect(jsonPath("$.grupos[0].observaciones").value("Excelente resoluciÃ³n"))
    }

    @Test
    fun `calificarAsignacion with path grupoId returns 200`() {
        val request = CalificarAsignacionRequestDTO(
            grupoId = 60L,
            calificacion = 8,
            observaciones = "Muy buen trabajo grupal"
        )
        val response = AsignacionResponseDTO(
            id = 5L,
            cursoId = 10L,
            titulo = "TP5",
            descripcion = null,
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl5",
            fechaLimite = null,
            grupos = listOf(
                GrupoAsignacionResponseDTO(
                    id = 60L,
                    nombre = "Grupo A",
                    integrantes = listOf("alumno1", "alumno2"),
                    repositorio = RepositorioDTO("repo5", "https://github.com/repo5", null, null, null),
                    calificacion = 8,
                    observaciones = "Muy buen trabajo grupal",
                )
            ),
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(asignacionService.calificarAsignacion(10L, 5L, "profe", request)).thenReturn(response)

        mockMvc.perform(
            post("/cursos/10/asignaciones/5/grupos/calificar")
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
            calificacion = 0,
            observaciones = "Desaprobado"
        )
        val auth = UsernamePasswordAuthenticationToken("profe", null)

        mockMvc.perform(
            post("/cursos/10/asignaciones/5/individual/calificar")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestUnderMin))
        )
            .andExpect(status().isBadRequest)

        val requestOverMax = CalificarAsignacionRequestDTO(
            calificacion = 11,
            observaciones = "Excelente plus"
        )

        mockMvc.perform(
            post("/cursos/10/asignaciones/5/individual/calificar")
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
