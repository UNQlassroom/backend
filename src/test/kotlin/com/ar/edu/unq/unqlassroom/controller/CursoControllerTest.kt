package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnoTeamMemberDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnoTeamMembershipDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.ObtenerAlumnosResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.RepositorioDTO
import com.ar.edu.unq.unqlassroom.service.CursoService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
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

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(cursoController).build()
    }

    @Test
    fun `crearCurso endpoint returns 201 and created curso with github team details`() {
        val requestDTO = CursoRequestDTO(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
        )

        val responseDTO = CursoResponseDTO(
            id = 10L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            descripcion = "Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1",
            githubTeamId = 987654L,
            githubTeamSlug = "2026s1_c1_estructuras_de_datos"
        )

        `when`(cursoService.crearCurso(requestDTO)).thenReturn(responseDTO)

        mockMvc.perform(
            post("/cursos/crear")
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
            .andExpect(jsonPath("$.githubTeamId").value(987654))
            .andExpect(jsonPath("$.githubTeamSlug").value("2026s1_c1_estructuras_de_datos"))
    }

    @Test
    fun `agregarAlumnos endpoint returns 200 and list of added students with repositorio`() {
        val requestDTO = AgregarAlumnosRequestDTO(
            usernames = listOf("alumno1", "alumno2")
        )

        val repoDTO = RepositorioDTO(
            nombre = "2026s1_c1_estructuras_de_datos_alumno1",
            htmlUrl = "https://github.com/UNQlassroom/2026s1_c1_estructuras_de_datos_alumno1",
            ultimoCommit = "Initial commit",
            fechaUltimoCommit = "2026-09-09T18:00:00Z",
            estadoCI = "sin_ci"
        )

        val responseDTO = AgregarAlumnosResponseDTO(
            cursoId = 10L,
            teamSlug = "2026s1_c1_estructuras_de_datos",
            alumnos = listOf(
                AlumnoTeamMembershipDTO(username = "alumno1", role = "member", state = "active", repositorio = repoDTO),
                AlumnoTeamMembershipDTO(username = "alumno2", role = "member", state = "pending", repositorio = null)
            )
        )

        `when`(cursoService.agregarAlumnos(10L, requestDTO)).thenReturn(responseDTO)

        mockMvc.perform(
            post("/cursos/10/alumnos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cursoId").value(10))
            .andExpect(jsonPath("$.teamSlug").value("2026s1_c1_estructuras_de_datos"))
            .andExpect(jsonPath("$.alumnos[0].username").value("alumno1"))
            .andExpect(jsonPath("$.alumnos[0].role").value("member"))
            .andExpect(jsonPath("$.alumnos[0].state").value("active"))
            .andExpect(jsonPath("$.alumnos[0].repositorio.nombre").value("2026s1_c1_estructuras_de_datos_alumno1"))
            .andExpect(jsonPath("$.alumnos[0].repositorio.ultimoCommit").value("Initial commit"))
            .andExpect(jsonPath("$.alumnos[0].repositorio.fechaUltimoCommit").value("2026-09-09T18:00:00Z"))
            .andExpect(jsonPath("$.alumnos[0].repositorio.estadoCI").value("sin_ci"))
            .andExpect(jsonPath("$.alumnos[1].username").value("alumno2"))
            .andExpect(jsonPath("$.alumnos[1].role").value("member"))
            .andExpect(jsonPath("$.alumnos[1].state").value("pending"))
    }

    @Test
    fun `agregarAlumnos endpoint returns 200 when usernames is empty`() {
        val requestDTO = AgregarAlumnosRequestDTO(usernames = emptyList())

        mockMvc.perform(
            post("/cursos/10/alumnos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO))
        )
            .andExpect(status().isOk)
    }

    @Test
    fun `obtenerAlumnos endpoint returns 200 and list of students with repositorio`() {
        val repoDTO = RepositorioDTO(
            nombre = "2026s1_c1_estructuras_de_datos_alumno1",
            htmlUrl = "https://github.com/UNQlassroom/2026s1_c1_estructuras_de_datos_alumno1",
            ultimoCommit = "Segundo commit",
            fechaUltimoCommit = "2026-09-09T19:00:00Z",
            estadoCI = "success"
        )

        val responseDTO = ObtenerAlumnosResponseDTO(
            cursoId = 10L,
            teamSlug = "2026s1_c1_estructuras_de_datos",
            alumnos = listOf(
                AlumnoTeamMemberDTO(username = "alumno1", role = "member", state = "active", repositorio = repoDTO)
            )
        )

        `when`(cursoService.obtenerAlumnos(10L)).thenReturn(responseDTO)

        mockMvc.perform(
            get("/cursos/10/alumnos")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.cursoId").value(10))
            .andExpect(jsonPath("$.teamSlug").value("2026s1_c1_estructuras_de_datos"))
            .andExpect(jsonPath("$.alumnos[0].username").value("alumno1"))
            .andExpect(jsonPath("$.alumnos[0].repositorio.nombre").value("2026s1_c1_estructuras_de_datos_alumno1"))
            .andExpect(jsonPath("$.alumnos[0].repositorio.ultimoCommit").value("Segundo commit"))
            .andExpect(jsonPath("$.alumnos[0].repositorio.estadoCI").value("success"))
    }
}
