package com.ar.edu.unq.unqlassroom.security

import com.ar.edu.unq.unqlassroom.dto.asignacion.request.CalificarAsignacionRequestDTO
import com.ar.edu.unq.unqlassroom.dto.asignacion.response.AsignacionResponseDTO
import com.ar.edu.unq.unqlassroom.dto.curso.request.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.model.Asignacion
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.GrupoAsignacion
import com.ar.edu.unq.unqlassroom.model.TipoAsignacion
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.AsignacionService
import com.ar.edu.unq.unqlassroom.service.CursoService
import com.ar.edu.unq.unqlassroom.security.JwtService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
class SecurityIntegrationTest {

    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtService: JwtService

    @MockitoBean
    private lateinit var cursoService: CursoService

    @MockitoBean
    private lateinit var asignacionService: AsignacionService

    @MockitoBean
    private lateinit var usuarioRepository: UsuarioRepository

    private val objectMapper = ObjectMapper()

    private val docente = Usuario(id = 1L, username = "profe", esDocente = true)
    private val alumno = Usuario(id = 2L, username = "alumno", esDocente = false)

    private fun anyCalificarModel(): GrupoAsignacion {
        Mockito.any(GrupoAsignacion::class.java)
        return GrupoAsignacion(calificacion = 8)
    }

    private fun anyCurso(): Curso {
        Mockito.any(Curso::class.java)
        return Curso(materia = "", anio = 0, semestre = 1, comision = 1)
    }

    private fun eqString(value: String): String {
        Mockito.eq(value)
        return value
    }

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity())
            .build()

        `when`(usuarioRepository.findByUsername("profe")).thenReturn(docente)
        `when`(usuarioRepository.findByUsername("alumno")).thenReturn(alumno)
    }

    @Test
    fun `public auth endpoints are accessible without token`() {
        mockMvc.perform(
            post("/auth/github")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `protected endpoints return 401 when token is missing`() {
        mockMvc.perform(get("/cursos"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `protected endpoints return 200 when valid token is provided`() {
        val token = jwtService.generateToken(alumno)
        `when`(cursoService.obtenerCursos("alumno", false)).thenReturn(emptyList())

        mockMvc.perform(
            get("/cursos")
                .header("Authorization", "Bearer $token")
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `crear curso returns 403 when user has role ALUMNO`() {
        val token = jwtService.generateToken(alumno)
        val requestDTO = CursoRequestDTO(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1
        )

        mockMvc.perform(
            post("/cursos")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `crear curso returns 201 when user has role DOCENTE`() {
        val token = jwtService.generateToken(docente)
        val requestDTO = CursoRequestDTO(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1
        )
        val cursoGuardado = Curso(
            id = 10L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            descripcion = "desc",
            owner = docente
        )

        `when`(cursoService.crearCurso(anyCurso(), eqString("profe"))).thenReturn(cursoGuardado)

        mockMvc.perform(
            post("/cursos")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO))
        )
            .andExpect(status().isCreated)
    }

    @Test
    fun `calificar asignacion returns 403 when user has role ALUMNO`() {
        val token = jwtService.generateToken(alumno)
        val request = CalificarAsignacionRequestDTO(grupoId = 1L, calificacion = 8, observaciones = "Buen trabajo")

        mockMvc.perform(
            post("/cursos/10/asignaciones/5/calificar")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `calificar asignacion returns 200 when user has role DOCENTE`() {
        val token = jwtService.generateToken(docente)
        val request = CalificarAsignacionRequestDTO(grupoId = 1L, calificacion = 8, observaciones = "Buen trabajo")
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 1, comision = 1)
        val asignacion = Asignacion(
            id = 5L,
            curso = curso,
            titulo = "TP5",
            descripcion = null,
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl5",
            fechaLimite = null,
            grupos = mutableListOf(),
        )

        `when`(asignacionService.calificarAsignacion(
            Mockito.eq(10L),
            Mockito.eq(5L),
            Mockito.eq(1L),
            Mockito.eq(8),
            Mockito.eq("Buen trabajo"),
            eqString("profe")
        )).thenReturn(asignacion)

        mockMvc.perform(
            post("/cursos/10/asignaciones/5/calificar")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
    }
}
