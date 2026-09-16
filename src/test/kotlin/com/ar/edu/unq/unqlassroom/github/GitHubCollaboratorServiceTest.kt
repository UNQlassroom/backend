package com.ar.edu.unq.unqlassroom.github

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class GitHubCollaboratorServiceTest {

    @Mock
    private lateinit var gitHubAppClient: GitHubAppClient

    @Mock
    private lateinit var properties: GitHubAppProperties

    private lateinit var gitHubCollaboratorService: GitHubCollaboratorService

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
        gitHubCollaboratorService = GitHubCollaboratorService(gitHubAppClient, properties)
    }

    @Test
    fun `addCollaborator sends PUT request and returns pending when invitation is created`() {
        val invitation = GitHubRepoInvitationItemResponse(
            id = 12345L,
            invitee = GitHubInviteeResponse(login = "userDeGithub"),
            permissions = "push"
        )
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(GitHubRepoInvitationItemResponse::class.java),
                body = anyString()
            )
        ).thenReturn(invitation)

        val result = gitHubCollaboratorService.addCollaborator(
            repoName = "2026s2_c3_programacion_funcional_userDeGithub",
            username = "userDeGithub",
            permission = "push"
        )

        assertEquals("userDeGithub", result.username)
        assertEquals("push", result.role)
        assertEquals("pending", result.state)

        verify(gitHubAppClient).executeInstallationRequest(
            method = anyString(),
            path = anyString(),
            responseType = anyClass(GitHubRepoInvitationItemResponse::class.java),
            body = anyString()
        )
    }

    @Test
    fun `addCollaborator returns active when user is already collaborator`() {
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(GitHubRepoInvitationItemResponse::class.java),
                body = anyString()
            )
        ).thenReturn(null)

        val result = gitHubCollaboratorService.addCollaborator(
            repoName = "2026s2_c3_programacion_funcional_userDeGithub",
            username = "userDeGithub",
            permission = "push"
        )

        assertEquals("userDeGithub", result.username)
        assertEquals("push", result.role)
        assertEquals("active", result.state)
    }

    @Test
    fun `getCollaborators sends GET request and returns direct collaborators`() {
        val collaborators = arrayOf(
            GitHubCollaboratorItemResponse(login = "alumno1", roleName = "admin"),
            GitHubCollaboratorItemResponse(login = "alumno2", roleName = "write")
        )
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(Array<GitHubCollaboratorItemResponse>::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(collaborators)

        val result = gitHubCollaboratorService.getCollaborators("test_repo")
        assertEquals(2, result.size)
        assertEquals("alumno1", result[0].login)
        assertEquals("admin", result[0].roleName)
    }

    @Test
    fun `getInvitations sends GET request and returns pending invitations`() {
        val invitations = arrayOf(
            GitHubRepoInvitationItemResponse(
                id = 1L,
                invitee = GitHubInviteeResponse(login = "alumno3"),
                permissions = "push"
            )
        )
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = anyString(),
                responseType = anyClass(Array<GitHubRepoInvitationItemResponse>::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(invitations)

        val result = gitHubCollaboratorService.getInvitations("test_repo")
        assertEquals(1, result.size)
        assertEquals("alumno3", result[0].invitee?.login)
    }

    @Test
    fun `getRepoMembers combines direct collaborators and pending invitations`() {
        val collaborators = arrayOf(
            GitHubCollaboratorItemResponse(login = "alumno1", roleName = "write")
        )
        val invitations = arrayOf(
            GitHubRepoInvitationItemResponse(
                id = 1L,
                invitee = GitHubInviteeResponse(login = "alumno2"),
                permissions = "push"
            )
        )
        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/collaborators?affiliation=direct&per_page=100"),
                responseType = anyClass(Array<GitHubCollaboratorItemResponse>::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(collaborators)
        `when`(
            gitHubAppClient.executeInstallationRequest(
                method = anyString(),
                path = eqString("/repos/UNQlassroom/test_repo/invitations?per_page=100"),
                responseType = anyClass(Array<GitHubRepoInvitationItemResponse>::class.java),
                body = Mockito.isNull()
            )
        ).thenReturn(invitations)

        val members = gitHubCollaboratorService.getRepoMembers("test_repo")
        assertEquals(2, members.size)
        assertEquals("alumno1", members[0].username)
        assertEquals("write", members[0].role)
        assertEquals("active", members[0].state)
        assertEquals("alumno2", members[1].username)
        assertEquals("push", members[1].role)
        assertEquals("pending", members[1].state)
    }
}
