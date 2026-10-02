package com.ar.edu.unq.unqlassroom.integration.github.service

import com.ar.edu.unq.unqlassroom.integration.github.client.GitHubAppClient
import com.ar.edu.unq.unqlassroom.integration.github.config.GitHubAppProperties

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class GitHubIssueServiceTest {

    @Mock
    private lateinit var gitHubAppClient: GitHubAppClient

    @Mock
    private lateinit var properties: GitHubAppProperties

    private lateinit var gitHubIssueService: GitHubIssueService

    private fun anyString(): String {
        Mockito.anyString()
        return ""
    }

    private fun <T> anyClass(clazz: Class<T>): Class<T> {
        Mockito.any(Class::class.java)
        return clazz
    }

    @BeforeEach
    fun setUp() {
        gitHubIssueService = GitHubIssueService(gitHubAppClient, properties)
    }

    @Test
    fun `getRepositoryIssues returns issues and filters out pull requests`() {
        val issue1 = GitHubIssueItemResponse(
            number = 1,
            title = "Corregir algoritmo",
            state = "open",
            htmlUrl = "https://github.com/UNQlassroom/repo1/issues/1",
            user = GitHubIssueUser(login = "docente"),
            comments = 1,
            createdAt = "2026-09-30T10:00:00Z",
            updatedAt = "2026-09-30T15:00:00Z",
            pullRequest = null,
        )
        val pullRequest = GitHubIssueItemResponse(
            number = 2,
            title = "PR de entrega",
            state = "open",
            htmlUrl = "https://github.com/UNQlassroom/repo1/pull/2",
            user = GitHubIssueUser(login = "alumno"),
            comments = 0,
            createdAt = "2026-09-30T11:00:00Z",
            updatedAt = "2026-09-30T11:00:00Z",
            pullRequest = Any(),
        )

        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(Array<GitHubIssueItemResponse>::class.java),
                body = Mockito.isNull(),
            )
        ).thenReturn(arrayOf(issue1, pullRequest))

        val result = gitHubIssueService.getRepositoryIssues("repo1")

        assertEquals(1, result.size)
        assertEquals(1, result[0].number)
        assertEquals("Corregir algoritmo", result[0].title)
    }

    @Test
    fun `getRepositoryIssues returns empty list when api call throws exception`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(Array<GitHubIssueItemResponse>::class.java),
                body = Mockito.isNull(),
            )
        ).thenThrow(RuntimeException("Network error"))

        val result = gitHubIssueService.getRepositoryIssues("repo1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getRepositoryIssues throws IllegalStateException if organization is not configured`() {
        `when`(properties.organization).thenReturn(null)

        val exception = assertThrows<IllegalStateException> {
            gitHubIssueService.getRepositoryIssues("repo1")
        }

        assertTrue(exception.message!!.contains("github.app.organization"))
    }
}
