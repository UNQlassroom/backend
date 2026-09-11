package com.ar.edu.unq.unqlassroom.github

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
class GitHubTeamServiceTest {

    @Mock
    private lateinit var gitHubAppClient: GitHubAppClient

    @Mock
    private lateinit var properties: GitHubAppProperties

    private lateinit var gitHubTeamService: GitHubTeamService

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
        gitHubTeamService = GitHubTeamService(gitHubAppClient, properties)
    }

    @Test
    fun `createTeam sends POST request to org teams endpoint`() {
        val expectedResponse = GitHubTeamResponse(
            id = 555L,
            nodeId = "node_555",
            name = "2026s1_c1_intro",
            slug = "2026s1_c1_intro",
            description = "Intro course"
        )

        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(GitHubTeamResponse::class.java),
                body = anyString()
            )
        ).thenReturn(expectedResponse)

        val result = gitHubTeamService.createTeam(
            name = "2026s1_c1_intro",
            description = "Intro course"
        )

        assertNotNull(result)
        assertEquals(555L, result.id)
        assertEquals("2026s1_c1_intro", result.name)

        verify(gitHubAppClient).executeInstallationRequest(
            method = anyString(),
            path = anyString(),
            responseType = anyClass(GitHubTeamResponse::class.java),
            body = anyString()
        )
    }

    @Test
    fun `createTeam throws exception when organization is blank and not provided`() {
        `when`(properties.organization).thenReturn("")

        val exception = assertThrows<IllegalStateException> {
            gitHubTeamService.createTeam(name = "team_test")
        }

        assertEquals("github.app.organization must be configured with the GitHub Organization name", exception.message)
    }

    @Test
    fun `addMemberToTeam sends PUT request to team memberships endpoint`() {
        val expectedResponse = GitHubTeamMembershipResponse(
            url = "https://api.github.com/organizations/1/team/240358/memberships/octocat",
            role = "member",
            state = "active"
        )

        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(GitHubTeamMembershipResponse::class.java),
                body = anyString()
            )
        ).thenReturn(expectedResponse)

        val result = gitHubTeamService.addMemberToTeam(
            teamSlug = "2026s1_c1_intro",
            username = "octocat"
        )

        assertNotNull(result)
        assertEquals("member", result.role)
        assertEquals("active", result.state)

        verify(gitHubAppClient).executeInstallationRequest(
            method = anyString(),
            path = anyString(),
            responseType = anyClass(GitHubTeamMembershipResponse::class.java),
            body = anyString()
        )
    }

    @Test
    fun `addMemberToTeam throws exception when organization is blank and not provided`() {
        `when`(properties.organization).thenReturn("")

        val exception = assertThrows<IllegalStateException> {
            gitHubTeamService.addMemberToTeam(
                teamSlug = "2026s1_c1_intro",
                username = "octocat"
            )
        }

        assertEquals("github.app.organization must be configured with the GitHub Organization name", exception.message)
    }
}
