package com.ar.edu.unq.unqlassroom.github

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class GitHubOrgServiceTest {

    @Mock
    private lateinit var gitHubAppClient: GitHubAppClient

    @Mock
    private lateinit var properties: GitHubAppProperties

    private lateinit var gitHubOrgService: GitHubOrgService

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
        gitHubOrgService = GitHubOrgService(gitHubAppClient, properties)
    }

    @Test
    fun `invitarMiembro sends PUT request to org memberships and returns response`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        val membership = GitHubOrgMembershipResponse(state = "pending", role = "member")

        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(GitHubOrgMembershipResponse::class.java),
                body = anyString()
            )
        ).thenReturn(membership)

        val result = gitHubOrgService.invitarMiembro("alumno1")

        assertEquals("pending", result.state)
        assertEquals("member", result.role)
        verify(gitHubAppClient).executeInstallationRequest(
            method = anyString(),
            path = anyString(),
            responseType = anyClass(GitHubOrgMembershipResponse::class.java),
            body = anyString()
        )
    }

    @Test
    fun `invitarMiembro returns pending when executeInstallationRequest throws exception`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(GitHubOrgMembershipResponse::class.java),
                body = anyString()
            )
        ).thenThrow(RuntimeException("API error"))

        val result = gitHubOrgService.invitarMiembro("alumno_inexistente")

        assertEquals("pending", result.state)
        assertEquals("member", result.role)
    }

    @Test
    fun `obtenerMembresia returns membership when user found`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        val membership = GitHubOrgMembershipResponse(state = "active", role = "member")

        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(GitHubOrgMembershipResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(membership)

        val result = gitHubOrgService.obtenerMembresia("alumno1")

        assertNotNull(result)
        assertEquals("active", result?.state)
        assertEquals("member", result?.role)
    }

    @Test
    fun `obtenerMembresia returns null when request fails`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(GitHubOrgMembershipResponse::class.java),
                body = Mockito.isNull()
            )
        ).thenThrow(RuntimeException("404 Not Found"))

        val result = gitHubOrgService.obtenerMembresia("alumno_no_invitado")

        assertNull(result)
    }
}
