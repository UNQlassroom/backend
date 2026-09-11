package com.ar.edu.unq.unqlassroom.github

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
    fun `addCollaborator sends PUT request to repo collaborators endpoint`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(Unit::class.java),
                body = anyString()
            )
        ).thenReturn(Unit)

        gitHubRepoService.addCollaborator(
            repoName = "2026s2_c3_programacion_funcional_userDeGithub",
            username = "userDeGithub",
            permission = "push"
        )

        verify(gitHubAppClient).executeInstallationRequest(
            method = anyString(),
            path = anyString(),
            responseType = anyClass(Unit::class.java),
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
    fun `generarNombreRepo produces the expected format with year, semester, comision, materia and username without tildes`() {
        val curso = com.ar.edu.unq.unqlassroom.model.Curso(
            materia = "programación funcional",
            anio = 2026,
            semestre = 2,
            comision = 3
        )
        assertEquals("2026s2_c3_programacion_funcional_userDeGithub", gitHubRepoService.generarNombreRepo(curso, "userDeGithub"))
    }

    @Test
    fun `generarNombreRepo removes accents from username as well`() {
        val curso = com.ar.edu.unq.unqlassroom.model.Curso(
            materia = "matemática discreta",
            anio = 2026,
            semestre = 1,
            comision = 1
        )
        assertEquals("2026s1_c1_matematica_discreta_agustin", gitHubRepoService.generarNombreRepo(curso, "agustín"))
    }

    @Test
    fun `generarDescripcionRepo produces the expected description`() {
        val curso = com.ar.edu.unq.unqlassroom.model.Curso(
            materia = "programación funcional",
            anio = 2026,
            semestre = 2,
            comision = 3
        )
        val desc = gitHubRepoService.generarDescripcionRepo(curso, "userDeGithub")
        assertEquals("Repositorio individual de userDeGithub para el curso programación funcional - Año 2026 - Semestre 2 - Comisión 3", desc)
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
}
