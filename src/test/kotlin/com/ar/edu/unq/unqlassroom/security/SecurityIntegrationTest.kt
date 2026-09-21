package com.ar.edu.unq.unqlassroom.security

import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.CursoService
import com.ar.edu.unq.unqlassroom.service.JwtService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
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
    private lateinit var usuarioRepository: UsuarioRepository

    private val objectMapper = ObjectMapper()

    private val docente = Usuario(id = 1L, username = "profe", esDocente = true)
    private val alumno = Usuario(id = 2L, username = "alumno", esDocente = false)

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
        `when`(cursoService.obtenerCursos()).thenReturn(emptyList())

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
            post("/cursos/crear")
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
        val responseDTO = CursoResponseDTO(
            id = 10L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            descripcion = "desc",
            ownerUsername = "profe"
        )

        `when`(cursoService.crearCurso(requestDTO, "profe")).thenReturn(responseDTO)

        mockMvc.perform(
            post("/cursos/crear")
                .header("Authorization", "Bearer $token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO))
        )
            .andExpect(status().isCreated)
    }
}
