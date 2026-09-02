package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.github.GitHubTeamResponse
import com.ar.edu.unq.unqlassroom.github.GitHubTeamService
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.service.impl.CursoServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

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
}
