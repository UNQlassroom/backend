package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.errors.CursoSinGitHubTeamAsociadoException
import com.ar.edu.unq.unqlassroom.github.GitHubTeamMembershipResponse
import com.ar.edu.unq.unqlassroom.github.GitHubTeamResponse
import com.ar.edu.unq.unqlassroom.github.GitHubTeamService
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.service.impl.CursoServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class CursoServiceImplTest {

    @Mock
    private lateinit var cursoRepository: CursoRepository

    @Mock
    private lateinit var gitHubTeamService: GitHubTeamService

    @InjectMocks
    private lateinit var cursoService: CursoServiceImpl

    private fun anyString(): String {
        Mockito.anyString()
        return ""
    }

    private fun anyCurso(): Curso {
        Mockito.any(Curso::class.java)
        return Curso(materia = "", anio = 0, semestre = 1, comision = 1)
    }

    @Test
    fun `crearCurso creates team on github and saves curso with team details`() {
        val requestDTO = CursoRequestDTO(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
        )

        val teamResponse = GitHubTeamResponse(
            id = 123456L,
            nodeId = "MDQ6VGVhbTEyMzQ1Ng==",
            name = "2026s1_c1_estructuras_de_datos",
            slug = "2026s1_c1_estructuras_de_datos",
            description = "Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1"
        )

        `when`(gitHubTeamService.createTeam(
            name = anyString(),
            description = anyString(),
            privacy = anyString(),
            org = Mockito.isNull()
        )).thenReturn(teamResponse)

        `when`(cursoRepository.save(anyCurso())).thenAnswer { invocation ->
            val curso = invocation.getArgument<Curso>(0)
            Curso(
                id = 1L,
                materia = curso.materia,
                anio = curso.anio,
                semestre = curso.semestre,
                comision = curso.comision,
                descripcion = curso.descripcion,
                githubTeamId = curso.githubTeamId,
                githubTeamSlug = curso.githubTeamSlug
            )
        }

        val result = cursoService.crearCurso(requestDTO)

        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals("Estructuras de Datos", result.materia)
        assertEquals(2026, result.anio)
        assertEquals(1, result.semestre)
        assertEquals(1, result.comision)
        assertEquals("Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1", result.descripcion)
        assertEquals(123456L, result.githubTeamId)
        assertEquals("2026s1_c1_estructuras_de_datos", result.githubTeamSlug)

        verify(gitHubTeamService).createTeam(
            name = "2026s1_c1_estructuras_de_datos",
            description = "Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1",
            privacy = "closed",
            org = null
        )
    }

    @Test
    fun `generarNombreTeam produces the expected snake_case format`() {
        val curso1 = Curso(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1
        )
        assertEquals("2026s1_c1_estructuras_de_datos", curso1.generarNombreTeam())

        val curso2 = Curso(
            materia = "Bases de Datos",
            anio = 2026,
            semestre = 2,
            comision = 2
        )
        assertEquals("2026s2_c2_bases_de_datos", curso2.generarNombreTeam())
    }

    @Test
    fun `generarDescripcionTeam produces the expected generic description`() {
        val curso = Curso(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1
        )
        assertEquals("Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1", curso.generarDescripcionTeam())
    }

    @Test
    fun `agregarAlumnos calls gitHubTeamService for each distinct username and returns response`() {
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            githubTeamId = 123456L,
            githubTeamSlug = "2026s1_c1_estructuras_de_datos"
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val membership1 = GitHubTeamMembershipResponse(
            url = "url/alumno1",
            role = "member",
            state = "active"
        )
        val membership2 = GitHubTeamMembershipResponse(
            url = "url/alumno2",
            role = "member",
            state = "pending"
        )

        `when`(gitHubTeamService.addMemberToTeam("2026s1_c1_estructuras_de_datos", "alumno1", "member", null))
            .thenReturn(membership1)
        `when`(gitHubTeamService.addMemberToTeam("2026s1_c1_estructuras_de_datos", "alumno2", "member", null))
            .thenReturn(membership2)

        val request = AgregarAlumnosRequestDTO(
            usernames = listOf("alumno1", "alumno2", "alumno1 ")
        )

        val response = cursoService.agregarAlumnos(1L, request)

        assertEquals(1L, response.cursoId)
        assertEquals("2026s1_c1_estructuras_de_datos", response.teamSlug)
        assertEquals(2, response.alumnos.size)
        assertEquals("alumno1", response.alumnos[0].username)
        assertEquals("active", response.alumnos[0].state)
        assertEquals("alumno2", response.alumnos[1].username)
        assertEquals("pending", response.alumnos[1].state)

        verify(gitHubTeamService).addMemberToTeam("2026s1_c1_estructuras_de_datos", "alumno1", "member", null)
        verify(gitHubTeamService).addMemberToTeam("2026s1_c1_estructuras_de_datos", "alumno2", "member", null)
    }

    @Test
    fun `agregarAlumnos throws CursoNotFoundException when curso does not exist`() {
        `when`(cursoRepository.findById(99L)).thenReturn(Optional.empty())

        val exception = assertThrows<com.ar.edu.unq.unqlassroom.errors.CursoNotFoundException> {
            cursoService.agregarAlumnos(99L, AgregarAlumnosRequestDTO(listOf("alumno1")))
        }

        assertEquals("Curso no encontrado", exception.message)
    }

    @Test
    fun `agregarAlumnos throws 400 BAD_REQUEST when curso has no github team slug`() {
        val curso = Curso(
            id = 2L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            githubTeamSlug = null
        )
        `when`(cursoRepository.findById(2L)).thenReturn(Optional.of(curso))

        val exception = assertThrows<CursoSinGitHubTeamAsociadoException> {
            cursoService.agregarAlumnos(2L, AgregarAlumnosRequestDTO(listOf("alumno1")))
        }

        assertEquals("Curso sin GitHub Team asociado", exception.message)
    }
}
