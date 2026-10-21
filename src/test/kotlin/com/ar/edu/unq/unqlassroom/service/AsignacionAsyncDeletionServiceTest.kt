package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.exception.GitHubNotFoundException
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.Asignacion
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.GrupoAsignacion
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.TipoAsignacion
import com.ar.edu.unq.unqlassroom.repository.AsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.IssueRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class AsignacionAsyncDeletionServiceTest {

    @Mock
    private lateinit var gitHubRepoService: GitHubRepoService

    @Mock
    private lateinit var asignacionRepository: AsignacionRepository

    @Mock
    private lateinit var issueRepository: IssueRepository

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    private lateinit var asyncDeletionService: AsignacionAsyncDeletionService

    @BeforeEach
    fun setUp() {
        asyncDeletionService = AsignacionAsyncDeletionService(
            gitHubRepoService = gitHubRepoService,
            asignacionRepository = asignacionRepository,
            issueRepository = issueRepository,
            transactionTemplate = transactionTemplate,
        )

        // Mock TransactionTemplate to immediately execute callback
        `when`(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer { invocation ->
            val callback = invocation.getArgument<TransactionCallback<Any>>(0)
            callback.doInTransaction(SimpleTransactionStatus())
        }
    }

    @Test
    fun `ejecutarEliminacionAsincronica deletes GitHub repositories, issues and assignment from DB`() {
        val curso = Curso(id = 1L, materia = "SO", anio = 2026, semestre = 1, comision = 1)
        val asignacion = Asignacion(
            id = 10L,
            curso = curso,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl"
        )
        curso.asignaciones.add(asignacion)

        val repo1 = Repositorio(id = 101L, nombre = "repo-1", htmlUrl = "https://github.com/repo-1")
        val repo2 = Repositorio(id = 102L, nombre = "repo-2", htmlUrl = "https://github.com/repo-2")
        val g1 = GrupoAsignacion(id = 1L, repositorio = repo1, asignacion = asignacion)
        val g2 = GrupoAsignacion(id = 2L, repositorio = repo2, asignacion = asignacion)
        asignacion.grupos.addAll(listOf(g1, g2))

        `when`(asignacionRepository.findById(10L)).thenReturn(Optional.of(asignacion))

        asyncDeletionService.ejecutarEliminacionAsincronica(10L, listOf("repo-1", "repo-2"))

        // Verify GitHub deletions
        verify(gitHubRepoService).deleteRepository("repo-1")
        verify(gitHubRepoService).deleteRepository("repo-2")

        // Verify Issue deletion
        verify(issueRepository).deleteAllByRepositorioIdIn(listOf(101L, 102L))

        // Verify Asignacion deletion
        verify(asignacionRepository).delete(asignacion)
    }

    @Test
    fun `ejecutarEliminacionAsincronica continues when a repository is not found in GitHub (404)`() {
        val asignacion = Asignacion(
            id = 10L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl"
        )
        val repo1 = Repositorio(id = 101L, nombre = "repo-1", htmlUrl = "https://github.com/repo-1")
        val g1 = GrupoAsignacion(id = 1L, repositorio = repo1, asignacion = asignacion)
        asignacion.grupos.add(g1)

        doThrow(GitHubNotFoundException("Repo no existe en GitHub"))
            .`when`(gitHubRepoService).deleteRepository("repo-1")
        `when`(asignacionRepository.findById(10L)).thenReturn(Optional.of(asignacion))

        asyncDeletionService.ejecutarEliminacionAsincronica(10L, listOf("repo-1", "repo-2"))

        verify(gitHubRepoService).deleteRepository("repo-1")
        verify(gitHubRepoService).deleteRepository("repo-2")
        verify(asignacionRepository).delete(asignacion)
    }

    @Test
    fun `ejecutarEliminacionAsincronica continues when GitHub throws generic Exception`() {
        val asignacion = Asignacion(
            id = 10L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl"
        )
        `when`(asignacionRepository.findById(10L)).thenReturn(Optional.of(asignacion))
        doThrow(RuntimeException("Network error"))
            .`when`(gitHubRepoService).deleteRepository("repo-1")

        asyncDeletionService.ejecutarEliminacionAsincronica(10L, listOf("repo-1"))

        verify(gitHubRepoService).deleteRepository("repo-1")
        verify(asignacionRepository).delete(asignacion)
    }

    @Test
    fun `ejecutarEliminacionAsincronica handles assignment already absent in database gracefully`() {
        `when`(asignacionRepository.findById(10L)).thenReturn(Optional.empty())

        asyncDeletionService.ejecutarEliminacionAsincronica(10L, listOf("repo-1"))

        verify(gitHubRepoService).deleteRepository("repo-1")
        verify(asignacionRepository, never()).delete(any())
    }
}
