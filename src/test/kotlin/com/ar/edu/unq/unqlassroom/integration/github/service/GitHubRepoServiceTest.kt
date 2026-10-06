package com.ar.edu.unq.unqlassroom.integration.github.service

import com.ar.edu.unq.unqlassroom.integration.github.client.GitHubAppClient
import com.ar.edu.unq.unqlassroom.integration.github.config.GitHubAppProperties

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class GitHubRepoServiceTest {

    @Mock
    private lateinit var gitHubAppClient: GitHubAppClient

    @Mock
    private lateinit var properties: GitHubAppProperties

    private lateinit var gitHubRepoService: GitHubRepoService

    private fun anyString(): String {
        Mockito.anyString()
        return ""
    }

    private fun eqString(value: String): String {
        Mockito.eq(value)
        return ""
    }

    private fun <T> anyClass(clazz: Class<T>): Class<T> {
        Mockito.any(Class::class.java)
        return clazz
    }

    @BeforeEach
    fun setUp() {
        gitHubRepoService = GitHubRepoService(gitHubAppClient, properties)
    }

    @Test
    fun `createOrgRepository sends POST request to org repos endpoint`() {
        val expectedResponse = GitHubRepoResponse(
            id = 999L,
            name = "2026s2_c3_programacion_funcional_userDeGithub",
            fullName = "UNQlassroom/2026s2_c3_programacion_funcional_userDeGithub",
            htmlUrl = "https://github.com/UNQlassroom/2026s2_c3_programacion_funcional_userDeGithub"
        )

        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(GitHubRepoResponse::class.java),
                body = anyString()
            )
        ).thenReturn(expectedResponse)

        val result = gitHubRepoService.createOrgRepository(
            name = "2026s2_c3_programacion_funcional_userDeGithub",
            description = "Repo de prueba",
            private = true,
            autoInit = true
        )

        assertNotNull(result)
        assertEquals(999L, result.id)
        assertEquals("2026s2_c3_programacion_funcional_userDeGithub", result.name)

        verify(gitHubAppClient).executeInstallationRequest(
            method = anyString(),
            path = anyString(),
            responseType = anyClass(GitHubRepoResponse::class.java),
            body = anyString()
        )
    }

    @Test
    fun `createOrgRepository throws exception when organization is blank`() {
        `when`(properties.organization).thenReturn("")

        val exception = assertThrows<IllegalStateException> {
            gitHubRepoService.createOrgRepository(name = "test_repo")
        }

        assertEquals("github.app.organization must be configured with the GitHub Organization name", exception.message)
    }

    @Test
    fun `repositoryExists returns true when resource exists`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(gitHubAppClient.checkResourceExists("/repos/UNQlassroom/existing_repo")).thenReturn(true)

        val result = gitHubRepoService.repositoryExists("existing_repo")
        assertEquals(true, result)
        verify(gitHubAppClient).checkResourceExists("/repos/UNQlassroom/existing_repo")
    }

    @Test
    fun `repositoryExists returns false when resource does not exist`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(gitHubAppClient.checkResourceExists("/repos/UNQlassroom/non_existing_repo")).thenReturn(false)

        val result = gitHubRepoService.repositoryExists("non_existing_repo")
        assertEquals(false, result)
        verify(gitHubAppClient).checkResourceExists("/repos/UNQlassroom/non_existing_repo")
    }

    @Test
    fun `repositoryExists throws exception when organization is blank`() {
        `when`(properties.organization).thenReturn("")

        val exception = assertThrows<IllegalStateException> {
            gitHubRepoService.repositoryExists(repoName = "test_repo")
        }

        assertEquals("github.app.organization must be configured with the GitHub Organization name", exception.message)
    }

    @Test
    fun `getRepository sends GET request to repo endpoint`() {
        val expected = GitHubRepoResponse(
            id = 123L,
            name = "test_repo",
            fullName = "UNQlassroom/test_repo",
            htmlUrl = "https://github.com/UNQlassroom/test_repo"
        )
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(GitHubRepoResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(expected)

        val repo = gitHubRepoService.getRepository("test_repo")
        assertEquals("test_repo", repo.name)
        assertEquals("https://github.com/UNQlassroom/test_repo", repo.htmlUrl)
    }

    @Test
    fun `getUltimoCommit returns latest commit info when commits exist`() {
        val commitResponse = arrayOf(
            GitHubCommitResponse(
                sha = "abc1234",
                commit = GitHubCommitData(
                    message = "Initial commit",
                    committer = GitHubCommitAuthor(name = "Dev", date = "2026-09-09T18:00:00Z")
                )
            )
        )
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(Array<GitHubCommitResponse>::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(commitResponse)

        val commit = gitHubRepoService.getUltimoCommit("test_repo")
        assertNotNull(commit)
        assertEquals("abc1234", commit?.sha)
        assertEquals("Initial commit", commit?.commit?.message)
        assertEquals("2026-09-09T18:00:00Z", commit?.commit?.committer?.date)
    }

    @Test
    fun `getUltimoCommit returns null when exception occurs or empty`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(Array<GitHubCommitResponse>::class.java),
                body = Mockito.isNull()
            )
        ).thenThrow(RuntimeException("Repo empty"))

        val commit = gitHubRepoService.getUltimoCommit("test_repo")
        assertNull(commit)
    }

    @Test
    fun `getEstadoCI returns success when all check runs succeed`() {
        val checkRunsResponse = GitHubCheckRunsResponse(
            totalCount = 1,
            checkRuns = listOf(
                GitHubCheckRunItem(name = "build", status = "completed", conclusion = "success")
            )
        )
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(GitHubCheckRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(checkRunsResponse)

        val status = gitHubRepoService.getEstadoCI("test_repo", "abc1234")
        assertEquals("success", status)
    }

    @Test
    fun `getEstadoCI returns failure when check run fails`() {
        val checkRunsResponse = GitHubCheckRunsResponse(
            totalCount = 1,
            checkRuns = listOf(
                GitHubCheckRunItem(name = "build", status = "completed", conclusion = "failure")
            )
        )
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(GitHubCheckRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(checkRunsResponse)

        val status = gitHubRepoService.getEstadoCI("test_repo", "abc1234")
        assertEquals("failure", status)
    }

    @Test
    fun `getEstadoCI returns sin_ci when sha is null`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        val status = gitHubRepoService.getEstadoCI("test_repo", null)
        assertEquals("sin_ci", status)
    }

    @Test
    fun `obtenerInformacionRepositorio consolidates commit and CI status`() {
        val commitResponse = arrayOf(
            GitHubCommitResponse(
                sha = "abc1234",
                commit = GitHubCommitData(
                    message = "Add README",
                    committer = GitHubCommitAuthor(name = "Dev", date = "2026-09-09T19:30:00Z")
                )
            )
        )
        val checkRunsResponse = GitHubCheckRunsResponse(
            totalCount = 1,
            checkRuns = listOf(
                GitHubCheckRunItem(name = "test", status = "completed", conclusion = "success")
            )
        )

        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/commits?per_page=1"),
                responseType = anyClass(Array<GitHubCommitResponse>::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(commitResponse)

        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/commits/abc1234/check-runs"),
                responseType = anyClass(GitHubCheckRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(checkRunsResponse)

        val info = gitHubRepoService.obtenerInformacionRepositorio("test_repo")
        assertEquals("test_repo", info.nombre)
        assertEquals("https://github.com/UNQlassroom/test_repo", info.htmlUrl)
        assertEquals("Add README", info.ultimoCommit)
        assertEquals("2026-09-09T19:30:00Z", info.fechaUltimoCommit)
        assertEquals("success", info.estadoCI)
    }

    @Test
    fun `createTemplateRepository delegates to createOrgRepository with isTemplate true`() {
        val expected = GitHubRepoResponse(
            id = 555L,
            name = "template_repo",
            isTemplate = true
        )
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = eqString("POST"),
                path = eqString("/orgs/UNQlassroom/repos"),
                responseType = anyClass(GitHubRepoResponse::class.java),
                body = anyString()
            )
        ).thenReturn(expected)

        val result = gitHubRepoService.createTemplateRepository("template_repo", "Template description")
        assertEquals(555L, result.id)
        assertEquals(true, result.isTemplate)
    }

    @Test
    fun `createRepositoryFromTemplate sends POST to generate endpoint with custom and default org`() {
        val expected = GitHubRepoResponse(id = 777L, name = "generated_repo")
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = eqString("POST"),
                path = eqString("/repos/UNQlassroom/tmpl/generate"),
                responseType = anyClass(GitHubRepoResponse::class.java),
                body = anyString()
            )
        ).thenReturn(expected)

        val result1 = gitHubRepoService.createRepositoryFromTemplate("tmpl", "generated_repo")
        assertEquals(777L, result1.id)

        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = eqString("POST"),
                path = eqString("/repos/CustomOrg/tmpl/generate"),
                responseType = anyClass(GitHubRepoResponse::class.java),
                body = anyString()
            )
        ).thenReturn(expected)

        val result2 = gitHubRepoService.createRepositoryFromTemplate("tmpl", "generated_repo", org = "CustomOrg")
        assertEquals(777L, result2.id)
    }

    @Test
    fun `listTemplateRepositories returns only repos marked as isTemplate`() {
        val repos = arrayOf(
            GitHubRepoResponse(id = 1L, name = "repo1", isTemplate = false),
            GitHubRepoResponse(id = 2L, name = "tmpl1", isTemplate = true),
            GitHubRepoResponse(id = 3L, name = "tmpl2", isTemplate = true)
        )
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = eqString("GET"),
                path = eqString("/orgs/UNQlassroom/repos?type=all&per_page=100"),
                responseType = anyClass(Array<GitHubRepoResponse>::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(repos)

        val result = gitHubRepoService.listTemplateRepositories()
        assertEquals(2, result.size)
        assertEquals(listOf("tmpl1", "tmpl2"), result.map { it.name })
    }

    @Test
    fun `createRelease sends POST to releases endpoint`() {
        val expected = GitHubReleaseResponse(id = 11L, tagName = "v1.0", htmlUrl = "https://release.url")
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = eqString("POST"),
                path = eqString("/repos/UNQlassroom/test_repo/releases"),
                responseType = anyClass(GitHubReleaseResponse::class.java),
                body = anyString()
            )
        ).thenReturn(expected)

        val result = gitHubRepoService.createRelease(
            repoName = "test_repo",
            tagName = "v1.0",
            name = "Release 1.0",
            body = "Notes",
            targetCommitish = "main"
        )
        assertEquals(11L, result.id)
        assertEquals("v1.0", result.tagName)
        assertEquals("https://release.url", result.htmlUrl)
    }

    @Test
    fun `getEstadoCI returns pending when check runs status is not completed`() {
        val checkRunsResponse = GitHubCheckRunsResponse(
            totalCount = 1,
            checkRuns = listOf(
                GitHubCheckRunItem(name = "build", status = "in_progress", conclusion = null)
            )
        )
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/commits/sha1/check-runs"),
                responseType = anyClass(GitHubCheckRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(checkRunsResponse)

        val status = gitHubRepoService.getEstadoCI("test_repo", "sha1")
        assertEquals("pending", status)
    }

    @Test
    fun `getEstadoCI returns success when check runs have neutral or skipped conclusion`() {
        val checkRunsResponse = GitHubCheckRunsResponse(
            totalCount = 2,
            checkRuns = listOf(
                GitHubCheckRunItem(name = "lint", status = "completed", conclusion = "neutral"),
                GitHubCheckRunItem(name = "deploy", status = "completed", conclusion = "skipped")
            )
        )
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/commits/sha1/check-runs"),
                responseType = anyClass(GitHubCheckRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(checkRunsResponse)

        val status = gitHubRepoService.getEstadoCI("test_repo", "sha1")
        assertEquals("success", status)
    }

    @Test
    fun `getEstadoCI falls back to commit status API when check runs empty or throws`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/commits/sha1/check-runs"),
                responseType = anyClass(GitHubCheckRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenThrow(RuntimeException("Not configured"))

        val statusResponse = GitHubCombinedStatusResponse(state = "success", totalCount = 1)
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/commits/sha1/status"),
                responseType = anyClass(GitHubCombinedStatusResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(statusResponse)

        val status = gitHubRepoService.getEstadoCI("test_repo", "sha1")
        assertEquals("success", status)
    }

    @Test
    fun `getEstadoCI falls back to workflow runs API when commit status empty or throws`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/commits/sha1/check-runs"),
                responseType = anyClass(GitHubCheckRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(GitHubCheckRunsResponse(totalCount = 0))

        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/commits/sha1/status"),
                responseType = anyClass(GitHubCombinedStatusResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(GitHubCombinedStatusResponse(state = "", totalCount = 0))

        val workflowRunsResponseFailure = GitHubWorkflowRunsResponse(
            totalCount = 1,
            workflowRuns = listOf(
                GitHubWorkflowRunItem(status = "completed", conclusion = "failure")
            )
        )
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/actions/runs?head_sha=sha1&per_page=5"),
                responseType = anyClass(GitHubWorkflowRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(workflowRunsResponseFailure)

        val status1 = gitHubRepoService.getEstadoCI("test_repo", "sha1")
        assertEquals("failure", status1)

        val workflowRunsResponsePending = GitHubWorkflowRunsResponse(
            totalCount = 1,
            workflowRuns = listOf(
                GitHubWorkflowRunItem(status = "in_progress", conclusion = null)
            )
        )
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/actions/runs?head_sha=sha1&per_page=5"),
                responseType = anyClass(GitHubWorkflowRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(workflowRunsResponsePending)

        val status2 = gitHubRepoService.getEstadoCI("test_repo", "sha1")
        assertEquals("pending", status2)

        val workflowRunsResponseSuccess = GitHubWorkflowRunsResponse(
            totalCount = 1,
            workflowRuns = listOf(
                GitHubWorkflowRunItem(status = "completed", conclusion = "neutral")
            )
        )
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/actions/runs?head_sha=sha1&per_page=5"),
                responseType = anyClass(GitHubWorkflowRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(workflowRunsResponseSuccess)

        val status3 = gitHubRepoService.getEstadoCI("test_repo", "sha1")
        assertEquals("success", status3)
    }

    @Test
    fun `getEstadoCI returns sin_ci when all APIs return empty or throw`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/commits/sha1/check-runs"),
                responseType = anyClass(GitHubCheckRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenThrow(RuntimeException("error"))

        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/commits/sha1/status"),
                responseType = anyClass(GitHubCombinedStatusResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenThrow(RuntimeException("error"))

        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/actions/runs?head_sha=sha1&per_page=5"),
                responseType = anyClass(GitHubWorkflowRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenThrow(RuntimeException("error"))

        val status = gitHubRepoService.getEstadoCI("test_repo", "sha1")
        assertEquals("sin_ci", status)
    }

    @Test
    fun `obtenerInformacionRepositorio uses author date when committer is null`() {
        val commitResponse = arrayOf(
            GitHubCommitResponse(
                sha = "abc1234",
                commit = GitHubCommitData(
                    message = "Commit author only",
                    committer = null,
                    author = GitHubCommitAuthor(name = "Author", date = "2026-09-10T12:00:00Z")
                )
            )
        )
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/commits?per_page=1"),
                responseType = anyClass(Array<GitHubCommitResponse>::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(commitResponse)

        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/commits/abc1234/check-runs"),
                responseType = anyClass(GitHubCheckRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(GitHubCheckRunsResponse(totalCount = 0))

        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/commits/abc1234/status"),
                responseType = anyClass(GitHubCombinedStatusResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(GitHubCombinedStatusResponse(totalCount = 0))

        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/actions/runs?head_sha=abc1234&per_page=5"),
                responseType = anyClass(GitHubWorkflowRunsResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(GitHubWorkflowRunsResponse(totalCount = 0))

        val info = gitHubRepoService.obtenerInformacionRepositorio("test_repo")
        assertEquals("Commit author only", info.ultimoCommit)
        assertEquals("2026-09-10T12:00:00Z", info.fechaUltimoCommit)
    }
}
