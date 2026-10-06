package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.dto.curso.request.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.dto.curso.request.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Inscripcion
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.service.CursoService
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
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class CursoControllerTest {

    @Mock
    private lateinit var cursoService: CursoService

    @InjectMocks
    private lateinit var cursoController: CursoController

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    private fun anyCurso(): Curso {
        any(Curso::class.java)
        return Curso(materia = "", anio = 0, semestre = 1, comision = 1)
    }

    private fun eqString(value: String): String {
        eq(value)
        return value
    }

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(cursoController).build()
    }

    @Test
    fun `crearCurso endpoint returns 201 and created curso`() {
        val requestDTO = CursoRequestDTO(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
        )

        val cursoGuardado = Curso(
            id = 10L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            descripcion = "Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1",
            owner = Usuario(id = 1L, username = "profe", esDocente = true)
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(cursoService.crearCurso(anyCurso(), eqString("profe"))).thenReturn(cursoGuardado)

        mockMvc.perform(
            post("/cursos")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.materia").value("Estructuras de Datos"))
            .andExpect(jsonPath("$.anio").value(2026))
            .andExpect(jsonPath("$.semestre").value(1))
            .andExpect(jsonPath("$.comision").value(1))
            .andExpect(jsonPath("$.descripcion").value("Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1"))
            .andExpect(jsonPath("$.ownerUsername").value("profe"))
    }

    @Test
    fun `agregarAlumnos endpoint returns 200 and list of added students`() {
        val requestDTO = AgregarAlumnosRequestDTO(
            usernames = listOf("alumno1", "alumno2")
        )

        val curso = Curso(id = 10L, materia = "Estructuras de Datos", anio = 2026, semestre = 1, comision = 1)
        val inscripciones = listOf(
            Inscripcion(id = 1L, curso = curso, usuario = Usuario(username = "alumno1"), githubRole = "push", githubState = "active"),
            Inscripcion(id = 2L, curso = curso, usuario = Usuario(username = "alumno2"), githubRole = "push", githubState = "pending")
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null, listOf(SimpleGrantedAuthority("ROLE_DOCENTE")))
        `when`(cursoService.agregarAlumnos(10L, listOf("alumno1", "alumno2"), "profe")).thenReturn(inscripciones)

        mockMvc.perform(
            post("/cursos/10/alumnos")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cursoId").value(10))
            .andExpect(jsonPath("$.alumnos[0].username").value("alumno1"))
            .andExpect(jsonPath("$.alumnos[0].role").value("push"))
            .andExpect(jsonPath("$.alumnos[0].state").value("active"))
            .andExpect(jsonPath("$.alumnos[1].username").value("alumno2"))
            .andExpect(jsonPath("$.alumnos[1].role").value("push"))
            .andExpect(jsonPath("$.alumnos[1].state").value("pending"))
    }

    @Test
    fun `agregarAlumnos endpoint returns 200 when usernames is empty`() {
        val requestDTO = AgregarAlumnosRequestDTO(usernames = emptyList())
        val auth = UsernamePasswordAuthenticationToken("profe", null, listOf(SimpleGrantedAuthority("ROLE_DOCENTE")))
        `when`(cursoService.agregarAlumnos(10L, emptyList(), "profe")).thenReturn(emptyList())

        mockMvc.perform(
            post("/cursos/10/alumnos")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `sincronizarAlumnos endpoint returns 200 and updated list of students`() {
        val curso = Curso(id = 10L, materia = "Estructuras de Datos", anio = 2026, semestre = 1, comision = 1)
        val inscripciones = listOf(
            Inscripcion(id = 1L, curso = curso, usuario = Usuario(username = "alumno1"), githubRole = "push", githubState = "active"),
            Inscripcion(id = 2L, curso = curso, usuario = Usuario(username = "alumno2"), githubRole = "push", githubState = "active")
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null, listOf(SimpleGrantedAuthority("ROLE_DOCENTE")))
        `when`(cursoService.sincronizarAlumnos(10L, "profe")).thenReturn(inscripciones)

        mockMvc.perform(
            post("/cursos/10/alumnos/sync")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cursoId").value(10))
            .andExpect(jsonPath("$.alumnos[0].username").value("alumno1"))
            .andExpect(jsonPath("$.alumnos[0].state").value("active"))
            .andExpect(jsonPath("$.alumnos[1].username").value("alumno2"))
            .andExpect(jsonPath("$.alumnos[1].state").value("active"))
    }

    @Test
    fun `obtenerAlumnos endpoint returns 200 and list of students`() {
        val curso = Curso(id = 10L, materia = "Estructuras de Datos", anio = 2026, semestre = 1, comision = 1)
        val inscripciones = listOf(
            Inscripcion(id = 1L, curso = curso, usuario = Usuario(username = "alumno1"), githubRole = "write", githubState = "active")
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null, listOf(SimpleGrantedAuthority("ROLE_DOCENTE")))
        `when`(cursoService.obtenerAlumnos(10L, "profe")).thenReturn(inscripciones)

        mockMvc.perform(
            get("/cursos/10/alumnos")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cursoId").value(10))
            .andExpect(jsonPath("$.alumnos[0].username").value("alumno1"))
            .andExpect(jsonPath("$.alumnos[0].role").value("write"))
            .andExpect(jsonPath("$.alumnos[0].state").value("active"))
    }

    @Test
    fun `obtenerCursos endpoint returns 200 and cursos for docente`() {
        val auth = UsernamePasswordAuthenticationToken("profe", null, listOf(SimpleGrantedAuthority("ROLE_DOCENTE")))
        val cursos = listOf(
            Curso(id = 1L, materia = "Estructuras", anio = 2026, semestre = 1, comision = 1, descripcion = "desc", owner = Usuario(username = "profe"))
        )
        `when`(cursoService.obtenerCursos("profe", true)).thenReturn(cursos)

        mockMvc.perform(
            get("/cursos")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.size()").value(1))
            .andExpect(jsonPath("$[0].materia").value("Estructuras"))
    }

    @Test
    fun `obtenerCursos endpoint returns 200 and cursos for alumno`() {
        val auth = UsernamePasswordAuthenticationToken("alumno", null, listOf(SimpleGrantedAuthority("ROLE_ALUMNO")))
        val cursos = listOf(
            Curso(id = 2L, materia = "Redes", anio = 2026, semestre = 1, comision = 2, descripcion = "desc", owner = Usuario(username = "otro_profe"))
        )
        `when`(cursoService.obtenerCursos("alumno", false)).thenReturn(cursos)

        mockMvc.perform(
            get("/cursos")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.size()").value(1))
            .andExpect(jsonPath("$[0].materia").value("Redes"))
    }

    @Test
    fun `obtenerCurso endpoint returns 200 and curso details`() {
        val auth = UsernamePasswordAuthenticationToken("profe", null, listOf(SimpleGrantedAuthority("ROLE_DOCENTE")))
        val curso = Curso(
            id = 1L,
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            descripcion = "desc",
            owner = Usuario(username = "profe")
        )
        `when`(cursoService.obtenerCurso(1L, "profe")).thenReturn(curso)

        mockMvc.perform(
            get("/cursos/1")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.materia").value("Estructuras"))
            .andExpect(jsonPath("$.ownerUsername").value("profe"))
    }
}
