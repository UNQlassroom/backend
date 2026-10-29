package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.exception.GitHubApiException
import com.ar.edu.unq.unqlassroom.exception.GitHubNotFoundException
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubCollaboratorService
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.integration.github.service.RepositorioInfo
import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.AsignacionRepository
import com.ar.edu.unq.unqlassroom.service.impl.AsyncServiceImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class AsyncServiceImplTest {

    @Mock
    private lateinit var gitHubRepoService: GitHubRepoService

    @Mock
    private lateinit var gitHubCollaboratorService: GitHubCollaboratorService

    @Mock
    private lateinit var asignacionRepository: AsignacionRepository

    @Mock
    private lateinit var templateStandardizationService: TemplateStandardizationService

    private lateinit var asyncService: AsyncService

    @BeforeEach
    fun setUp() {
        asyncService = AsyncServiceImpl(
            gitHubRepoService = gitHubRepoService,
            gitHubCollaboratorService = gitHubCollaboratorService,
            asignacionRepository = asignacionRepository,
            templateStandardizationService = templateStandardizationService,
        )
    }

    @Test
    fun `crearRepositoriosAsignacionAsincronicamente INDIVIDUAL creates repos, adds collaborators and marks asignacion as LISTA`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 10L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = docente,
        )
        val alumno1 = Usuario(id = 2L, username = "alumno1")
        val alumno2 = Usuario(id = 3L, username = "alumno2")

        val asignacion = Asignacion(
            id = 100L,
            titulo = "TP1",
            descripcion = "Primer trabajo individual",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "template-tp1",
            estado = EstadoAsignacion.CREANDO,
            curso = curso,
        )
        val grupo1 = GrupoAsignacion(
            id = 10L,
            nombre = null,
            asignacion = asignacion,
            repositorio = null,
            integrantes = mutableListOf(alumno1),
        )
        val grupo2 = GrupoAsignacion(
            id = 20L,
            nombre = null,
            asignacion = asignacion,
            repositorio = null,
            integrantes = mutableListOf(alumno2),
        )
        asignacion.grupos.addAll(listOf(grupo1, grupo2))

        val repoName1 = "2026s1_c1_estructuras_de_datos_tp1_alumno1"
        val repoName2 = "2026s1_c1_estructuras_de_datos_tp1_alumno2"

        `when`(asignacionRepository.findById(100L)).thenReturn(Optional.of(asignacion))
        `when`(gitHubRepoService.repositoryExists(repoName1)).thenReturn(false)
        `when`(gitHubRepoService.repositoryExists(repoName2)).thenReturn(false)

        val repoInfo1 = RepositorioInfo(
            nombre = repoName1,
            htmlUrl = "https://github.com/UNQlassroom/$repoName1",
            ultimoCommit = "Initial commit",
            fechaUltimoCommit = "2026-09-24T18:00:00Z",
            estadoCI = "success",
        )
        val repoInfo2 = RepositorioInfo(
            nombre = repoName2,
            htmlUrl = "https://github.com/UNQlassroom/$repoName2",
            ultimoCommit = "Initial commit",
            fechaUltimoCommit = "2026-09-24T18:00:00Z",
            estadoCI = "pending",
        )
        `when`(gitHubRepoService.obtenerInformacionRepositorio(repoName1)).thenReturn(repoInfo1)
        `when`(gitHubRepoService.obtenerInformacionRepositorio(repoName2)).thenReturn(repoInfo2)

        asyncService.crearRepositoriosAsignacionAsincronicamente(100L, "profe_test")

        assertEquals(EstadoAsignacion.LISTA, asignacion.estado)
        assertNotNull(grupo1.repositorio)
        assertEquals(repoName1, grupo1.repositorio?.nombre)
        assertEquals("success", grupo1.repositorio?.estadoCI)
        assertNotNull(grupo2.repositorio)
        assertEquals(repoName2, grupo2.repositorio?.nombre)
        assertEquals("pending", grupo2.repositorio?.estadoCI)

        verify(templateStandardizationService).asegurarTemplateEstandarizado("template-tp1")
        verify(gitHubRepoService).createRepositoryFromTemplate(
            templateRepoName = "template-tp1",
            newRepoName = repoName1,
            description = "Repositorio de asignación 'TP1' (alumno1) - Estructuras de Datos (2026s1 comision 1)",
            private = true,
        )
        verify(gitHubCollaboratorService).addCollaborator(repoName1, "alumno1", "push")
        verify(gitHubCollaboratorService).addCollaborator(repoName1, "profe_test", "push")

        verify(gitHubRepoService).createRepositoryFromTemplate(
            templateRepoName = "template-tp1",
            newRepoName = repoName2,
            description = "Repositorio de asignación 'TP1' (alumno2) - Estructuras de Datos (2026s1 comision 1)",
            private = true,
        )
        verify(gitHubCollaboratorService).addCollaborator(repoName2, "alumno2", "push")
        verify(gitHubCollaboratorService).addCollaborator(repoName2, "profe_test", "push")
        verify(asignacionRepository).save(asignacion)
    }

    @Test
    fun `crearRepositoriosAsignacionAsincronicamente GRUPAL creates shared repo, adds members and marks asignacion as LISTA`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 10L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = docente,
        )
        val alumno1 = Usuario(id = 2L, username = "alumno1")
        val alumno2 = Usuario(id = 3L, username = "alumno2")

        val asignacion = Asignacion(
            id = 200L,
            titulo = "TP2",
            descripcion = "Trabajo grupal",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "template-tp2",
            estado = EstadoAsignacion.CREANDO,
            curso = curso,
        )
        val grupo = GrupoAsignacion(
            id = 30L,
            nombre = "Grupo Alpha",
            asignacion = asignacion,
            repositorio = null,
            integrantes = mutableListOf(alumno1, alumno2),
        )
        asignacion.grupos.add(grupo)

        val repoName = "2026s1_c1_estructuras_de_datos_tp2_grupo_alpha"
        `when`(asignacionRepository.findById(200L)).thenReturn(Optional.of(asignacion))
        `when`(gitHubRepoService.repositoryExists(repoName)).thenReturn(false)

        val repoInfo = RepositorioInfo(
            nombre = repoName,
            htmlUrl = "https://github.com/UNQlassroom/$repoName",
            ultimoCommit = "Initial commit",
            fechaUltimoCommit = "2026-09-24T18:00:00Z",
            estadoCI = "sin_ci",
        )
        `when`(gitHubRepoService.obtenerInformacionRepositorio(repoName)).thenReturn(repoInfo)

        asyncService.crearRepositoriosAsignacionAsincronicamente(200L, "profe_test")

        assertEquals(EstadoAsignacion.LISTA, asignacion.estado)
        assertNotNull(grupo.repositorio)
        assertEquals(repoName, grupo.repositorio?.nombre)

        verify(templateStandardizationService).asegurarTemplateEstandarizado("template-tp2")
        verify(gitHubRepoService).createRepositoryFromTemplate(
            templateRepoName = "template-tp2",
            newRepoName = repoName,
            description = "Repositorio de asignación 'TP2' (Grupo Alpha) - Estructuras de Datos (2026s1 comision 1)",
            private = true,
        )
        verify(gitHubCollaboratorService).addCollaborator(repoName, "alumno1", "push")
        verify(gitHubCollaboratorService).addCollaborator(repoName, "alumno2", "push")
        verify(gitHubCollaboratorService).addCollaborator(repoName, "profe_test", "push")
        verify(asignacionRepository).save(asignacion)
    }

    @Test
    fun `crearRepositoriosAsignacionAsincronicamente rolls back created repos and sets estado ERROR when GitHub fails`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 10L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = docente,
        )
        val alumno1 = Usuario(id = 2L, username = "alumno1")
        val alumno2 = Usuario(id = 3L, username = "alumno2")

        val asignacion = Asignacion(
            id = 100L,
            titulo = "TP1",
            descripcion = "Primer trabajo individual",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "template-tp1",
            estado = EstadoAsignacion.CREANDO,
            curso = curso,
        )
        val grupo1 = GrupoAsignacion(
            id = 10L,
            nombre = null,
            asignacion = asignacion,
            repositorio = null,
            integrantes = mutableListOf(alumno1),
        )
        val grupo2 = GrupoAsignacion(
            id = 20L,
            nombre = null,
            asignacion = asignacion,
            repositorio = null,
            integrantes = mutableListOf(alumno2),
        )
        asignacion.grupos.addAll(listOf(grupo1, grupo2))

        val repoName1 = "2026s1_c1_estructuras_de_datos_tp1_alumno1"
        val repoName2 = "2026s1_c1_estructuras_de_datos_tp1_alumno2"

        `when`(asignacionRepository.findById(100L)).thenReturn(Optional.of(asignacion))
        `when`(gitHubRepoService.repositoryExists(repoName1)).thenReturn(false)
        `when`(gitHubRepoService.repositoryExists(repoName2)).thenReturn(false)

        val repoInfo1 = RepositorioInfo(
            nombre = repoName1,
            htmlUrl = "https://github.com/UNQlassroom/$repoName1",
            ultimoCommit = "Initial commit",
            fechaUltimoCommit = "2026-09-24T18:00:00Z",
            estadoCI = "success",
        )
        `when`(gitHubRepoService.obtenerInformacionRepositorio(repoName1)).thenReturn(repoInfo1)

        doThrow(GitHubApiException(500, "GitHub internal error"))
            .`when`(gitHubRepoService).createRepositoryFromTemplate(
                templateRepoName = "template-tp1",
                newRepoName = repoName2,
                description = "Repositorio de asignación 'TP1' (alumno2) - Estructuras de Datos (2026s1 comision 1)",
                private = true,
            )

        asyncService.crearRepositoriosAsignacionAsincronicamente(100L, "profe_test")

        assertEquals(EstadoAsignacion.ERROR, asignacion.estado)
        assertNull(grupo1.repositorio)
        assertNull(grupo2.repositorio)
        verify(gitHubRepoService).deleteRepository(repoName1)
        verify(gitHubRepoService, never()).deleteRepository(repoName2)
        verify(asignacionRepository).save(asignacion)
    }

    @Test
    fun `crearRepositoriosAsignacionAsincronicamente does nothing when asignacion is not found`() {
        `when`(asignacionRepository.findById(999L)).thenReturn(Optional.empty())

        asyncService.crearRepositoriosAsignacionAsincronicamente(999L, "profe_test")

        verifyNoInteractions(gitHubRepoService)
        verifyNoInteractions(gitHubCollaboratorService)
        verifyNoInteractions(templateStandardizationService)
        verify(asignacionRepository, never()).save(any(Asignacion::class.java))
    }

    @Test
    fun `eliminarRepositoriosAsincronicamente deletes all GitHub repositories`() {
        asyncService.eliminarRepositoriosAsincronicamente(listOf("repo-1", "repo-2"))

        verify(gitHubRepoService).deleteRepository("repo-1")
        verify(gitHubRepoService).deleteRepository("repo-2")
    }

    @Test
    fun `eliminarRepositoriosAsincronicamente does nothing when list is empty`() {
        asyncService.eliminarRepositoriosAsincronicamente(emptyList())

        verifyNoInteractions(gitHubRepoService)
    }

    @Test
    fun `eliminarRepositoriosAsincronicamente continues when a repository is not found in GitHub (404)`() {
        doThrow(GitHubNotFoundException("Repo no existe en GitHub"))
            .`when`(gitHubRepoService).deleteRepository("repo-1")

        asyncService.eliminarRepositoriosAsincronicamente(listOf("repo-1", "repo-2"))

        verify(gitHubRepoService).deleteRepository("repo-1")
        verify(gitHubRepoService).deleteRepository("repo-2")
    }

    @Test
    fun `eliminarRepositoriosAsincronicamente continues when GitHub throws generic Exception`() {
        doThrow(RuntimeException("Network error"))
            .`when`(gitHubRepoService).deleteRepository("repo-1")

        asyncService.eliminarRepositoriosAsincronicamente(listOf("repo-1", "repo-2"))

        verify(gitHubRepoService).deleteRepository("repo-1")
        verify(gitHubRepoService).deleteRepository("repo-2")
    }
}
