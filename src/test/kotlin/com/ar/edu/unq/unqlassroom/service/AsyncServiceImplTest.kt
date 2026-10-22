package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.exception.GitHubNotFoundException
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.service.impl.AsyncServiceImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class AsyncServiceImplTest {

    @Mock
    private lateinit var gitHubRepoService: GitHubRepoService

    private lateinit var asyncService: AsyncService

    @BeforeEach
    fun setUp() {
        asyncService = AsyncServiceImpl(
            gitHubRepoService = gitHubRepoService,
        )
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
