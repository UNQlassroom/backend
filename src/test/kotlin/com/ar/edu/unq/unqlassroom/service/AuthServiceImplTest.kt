package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.dto.auth.request.GitHubLoginRequestDTO
import com.ar.edu.unq.unqlassroom.integration.github.config.GitHubAppProperties
import com.ar.edu.unq.unqlassroom.integration.github.client.GitHubOAuthClient
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubOrgMembershipResponse
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubOrgService
import com.ar.edu.unq.unqlassroom.integration.github.client.GitHubUserProfileResponse
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.impl.AuthServiceImpl
import com.ar.edu.unq.unqlassroom.security.JwtService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.security.core.userdetails.UsernameNotFoundException

@ExtendWith(MockitoExtension::class)
class AuthServiceImplTest {

    @Mock
    private lateinit var gitHubOAuthClient: GitHubOAuthClient

    @Mock
    private lateinit var usuarioRepository: UsuarioRepository

    @Mock
    private lateinit var jwtService: JwtService

    @Mock
    private lateinit var gitHubOrgService: GitHubOrgService

    @Mock
    private lateinit var properties: GitHubAppProperties

    @InjectMocks
    private lateinit var authService: AuthServiceImpl

    private fun anyUsuario(): Usuario {
        Mockito.any(Usuario::class.java)
        return Usuario(username = "")
    }

    private fun anyString(): String {
        Mockito.anyString()
        return ""
    }

    private fun anyBoolean(): Boolean {
        Mockito.anyBoolean()
        return false
    }

    @Test
    fun `loginConGitHub creates new alumno when user does not exist`() {
        val dto = GitHubLoginRequestDTO(code = "oauth-code-123", esDocente = false)
        val profile = GitHubUserProfileResponse(
            login = "alumno_nuevo",
            id = 12345L,
            name = "Alumno Nuevo",
            email = "alumno@unq.edu.ar",
            avatar_url = "https://avatars.githubusercontent.com/u/12345"
        )

        `when`(gitHubOAuthClient.intercambiarCodePorToken("oauth-code-123")).thenReturn("gho_test_token")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_test_token")).thenReturn(profile)
        `when`(usuarioRepository.findByUsername("alumno_nuevo")).thenReturn(null)
        `when`(usuarioRepository.save(anyUsuario())).thenAnswer { invocation ->
            val u = invocation.getArgument<Usuario>(0)
            Usuario(
                id = 10L,
                username = u.username,
                email = u.email,
                nombreCompleto = u.nombreCompleto
            )
        }
        `when`(jwtService.generateToken(anyUsuario(), anyBoolean())).thenReturn("jwt-token-xyz")

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertEquals("jwt-token-xyz", response.token)
        assertEquals("alumno_nuevo", response.user?.username)
        assertEquals(false, response.user?.esDocente)
        assertNull(response.user?.nombreCompleto)
    }

    @Test
    fun `loginConGitHub creates new docente when esDocente is true`() {
        val dto = GitHubLoginRequestDTO(code = "oauth-code-docente", esDocente = true)
        val profile = GitHubUserProfileResponse(
            login = "profe_nuevo",
            id = 54321L,
            name = "Profesor",
            email = "profe@unq.edu.ar",
            avatar_url = null
        )

        `when`(gitHubOAuthClient.intercambiarCodePorToken("oauth-code-docente")).thenReturn("gho_docente_token")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_docente_token")).thenReturn(profile)
        `when`(usuarioRepository.findByUsername("profe_nuevo")).thenReturn(null)
        `when`(usuarioRepository.save(anyUsuario())).thenAnswer { invocation ->
            val u = invocation.getArgument<Usuario>(0)
            Usuario(
                id = 20L,
                username = u.username,
                email = u.email,
                nombreCompleto = u.nombreCompleto
            )
        }
        `when`(jwtService.generateToken(anyUsuario(), anyBoolean())).thenReturn("jwt-docente-token")

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertEquals("jwt-docente-token", response.token)
        assertEquals(true, response.user?.esDocente)
    }

    @Test
    fun `loginConGitHub returns user with esDocente according to login request`() {
        val dto = GitHubLoginRequestDTO(code = "oauth-code-existing", esDocente = false)
        val profile = GitHubUserProfileResponse(
            login = "profe_existente",
            id = 999L,
            name = "Profesor Existente",
            email = "profe.actualizado@unq.edu.ar",
            avatar_url = null
        )

        val existingDocente = Usuario(
            id = 5L,
            username = "profe_existente",
            email = "profe.viejo@unq.edu.ar",
            nombreCompleto = "Profesor",
            githubState = "active"
        )

        `when`(gitHubOAuthClient.intercambiarCodePorToken("oauth-code-existing")).thenReturn("gho_token_existing")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_token_existing")).thenReturn(profile)
        `when`(usuarioRepository.findByUsername("profe_existente")).thenReturn(existingDocente)
        `when`(usuarioRepository.save(existingDocente)).thenReturn(existingDocente)
        `when`(jwtService.generateToken(anyUsuario(), anyBoolean())).thenReturn("jwt-token-docente")

        val response = authService.loginConGitHub(dto)

        assertEquals("jwt-token-docente", response.token)
        assertEquals(false, response.user?.esDocente)
        assertEquals("Profesor", response.user?.nombreCompleto)
        verifyNoInteractions(gitHubOrgService)
    }

    @Test
    fun `loadUserByUsername returns UserDetails with username`() {
        val usuario = Usuario(id = 1L, username = "docente1")
        `when`(usuarioRepository.findByUsername("docente1")).thenReturn(usuario)

        val userDetailsDocente = authService.loadUserByUsername("docente1")

        assertEquals("docente1", userDetailsDocente.username)
        assertTrue(userDetailsDocente.authorities.any { it.authority == "ROLE_USER" })
    }

    @Test
    fun `loadUserByUsername throws UsernameNotFoundException when user does not exist`() {
        `when`(usuarioRepository.findByUsername("desconocido")).thenReturn(null)

        assertThrows<UsernameNotFoundException> {
            authService.loadUserByUsername("desconocido")
        }
    }

    @Test
    fun `loginConGitHub invites user and returns redirect response when new user is not member of org`() {
        val dto = GitHubLoginRequestDTO(code = "code-no-org", esDocente = false)
        val profile = GitHubUserProfileResponse(
            login = "usuario_sin_org",
            id = 555L,
            name = "Usuario Sin Org",
            email = "sinorg@unq.edu.ar",
            avatar_url = null
        )

        Mockito.lenient().`when`(properties.organization).thenReturn("UNQlassroom")
        `when`(gitHubOAuthClient.intercambiarCodePorToken("code-no-org")).thenReturn("gho_sin_org")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_sin_org")).thenReturn(profile)
        `when`(usuarioRepository.findByUsername("usuario_sin_org")).thenReturn(null)
        `when`(gitHubOrgService.invitarMiembro(username = "usuario_sin_org", role = "member"))
            .thenReturn(GitHubOrgMembershipResponse(state = "pending", role = "member"))
        `when`(usuarioRepository.save(anyUsuario())).thenAnswer { invocation ->
            val u = invocation.getArgument<Usuario>(0)
            Usuario(
                id = 99L,
                username = u.username,
                email = u.email,
                nombreCompleto = u.nombreCompleto,
                githubState = u.githubState
            )
        }

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertTrue(response.requiereUnirseAOrg)
        assertEquals("https://github.com/orgs/UNQlassroom/invitation", response.redirectUrl)
        assertNull(response.token)
        assertNull(response.user)
        verify(gitHubOrgService).invitarMiembro(username = "usuario_sin_org", role = "member")
    }

    @Test
    fun `loginConGitHub invites user as member when user is new docente and not member of org`() {
        val dto = GitHubLoginRequestDTO(code = "code-admin-no-org", esDocente = true)
        val profile = GitHubUserProfileResponse(
            login = "docente_sin_org",
            id = 666L,
            name = "Docente Sin Org",
            email = "docente_sin_org@unq.edu.ar",
            avatar_url = null
        )

        Mockito.lenient().`when`(properties.organization).thenReturn("UNQlassroom")
        `when`(gitHubOAuthClient.intercambiarCodePorToken("code-admin-no-org")).thenReturn("gho_admin_sin_org")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_admin_sin_org")).thenReturn(profile)
        `when`(usuarioRepository.findByUsername("docente_sin_org")).thenReturn(null)
        `when`(gitHubOrgService.invitarMiembro(username = "docente_sin_org", role = "member"))
            .thenReturn(GitHubOrgMembershipResponse(state = "pending", role = "member"))
        `when`(usuarioRepository.save(anyUsuario())).thenAnswer { invocation ->
            val u = invocation.getArgument<Usuario>(0)
            Usuario(
                id = 100L,
                username = u.username,
                email = u.email,
                nombreCompleto = u.nombreCompleto,
                githubState = u.githubState
            )
        }

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertTrue(response.requiereUnirseAOrg)
        assertEquals("https://github.com/orgs/UNQlassroom/invitation", response.redirectUrl)
        assertNull(response.token)
        assertNull(response.user)
        verify(gitHubOrgService).invitarMiembro(username = "docente_sin_org", role = "member")
    }

    @Test
    fun `loginConGitHub sets githubState active and generates token when new user invitation is immediately active`() {
        val dto = GitHubLoginRequestDTO(code = "code-active-immediate", esDocente = false)
        val profile = GitHubUserProfileResponse(
            login = "usuario_nuevo_activo",
            id = 777L,
            name = "Usuario Nuevo Activo",
            email = "nuevo_activo@unq.edu.ar",
            avatar_url = null
        )

        Mockito.lenient().`when`(properties.organization).thenReturn("UNQlassroom")
        `when`(gitHubOAuthClient.intercambiarCodePorToken("code-active-immediate")).thenReturn("gho_active_immediate")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_active_immediate")).thenReturn(profile)
        `when`(usuarioRepository.findByUsername("usuario_nuevo_activo")).thenReturn(null)
        `when`(gitHubOrgService.invitarMiembro(username = "usuario_nuevo_activo", role = "member"))
            .thenReturn(GitHubOrgMembershipResponse(state = "active", role = "member"))
        `when`(usuarioRepository.save(anyUsuario())).thenAnswer { invocation ->
            val u = invocation.getArgument<Usuario>(0)
            Usuario(
                id = 88L,
                username = u.username,
                email = u.email,
                nombreCompleto = u.nombreCompleto,
                githubState = u.githubState
            )
        }
        `when`(jwtService.generateToken(anyUsuario(), anyBoolean())).thenReturn("jwt-active-token")

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertEquals("jwt-active-token", response.token)
        assertFalse(response.requiereUnirseAOrg)
        assertNull(response.redirectUrl)
        verify(gitHubOrgService).invitarMiembro(username = "usuario_nuevo_activo", role = "member")
    }

    @Test
    fun `loginConGitHub does not call gitHubOrgService when user exists in DB and is active`() {
        val dto = GitHubLoginRequestDTO(code = "oauth-code-existing", esDocente = false)
        val profile = GitHubUserProfileResponse(
            login = "usuario_existente",
            id = 111L,
            name = "Usuario Existente",
            email = "existente@unq.edu.ar",
            avatar_url = null
        )
        val existingUser = Usuario(
            id = 1L,
            username = "usuario_existente",
            email = "existente@unq.edu.ar",
            nombreCompleto = "Usuario Existente",
            githubState = "active"
        )

        Mockito.lenient().`when`(properties.organization).thenReturn("UNQlassroom")
        `when`(gitHubOAuthClient.intercambiarCodePorToken("oauth-code-existing")).thenReturn("gho_existing")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_existing")).thenReturn(profile)
        `when`(usuarioRepository.findByUsername("usuario_existente")).thenReturn(existingUser)
        `when`(usuarioRepository.save(existingUser)).thenReturn(existingUser)
        `when`(jwtService.generateToken(anyUsuario(), anyBoolean())).thenReturn("jwt-existing")

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertEquals("jwt-existing", response.token)
        assertEquals(false, response.requiereUnirseAOrg)
        assertNull(response.redirectUrl)
        verifyNoInteractions(gitHubOrgService)
    }

    @Test
    fun `loginConGitHub does not call gitHubOrgService and redirects directly when user exists in DB with pending state`() {
        val dto = GitHubLoginRequestDTO(code = "oauth-code-pending-student", esDocente = false)
        val profile = GitHubUserProfileResponse(
            login = "alumno_pendiente",
            id = 222L,
            name = "Alumno Pendiente",
            email = "pendiente@unq.edu.ar",
            avatar_url = null
        )
        val existingStudent = Usuario(
            id = 2L,
            username = "alumno_pendiente",
            email = "pendiente@unq.edu.ar",
            nombreCompleto = "Alumno Pendiente",
            githubState = "pending"
        )

        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(gitHubOAuthClient.intercambiarCodePorToken("oauth-code-pending-student")).thenReturn("gho_student")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_student")).thenReturn(profile)
        `when`(usuarioRepository.findByUsername("alumno_pendiente")).thenReturn(existingStudent)

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertTrue(response.requiereUnirseAOrg)
        assertEquals("https://github.com/orgs/UNQlassroom/invitation", response.redirectUrl)
        assertNull(response.token)
        verifyNoInteractions(gitHubOrgService)
        verify(usuarioRepository).save(existingStudent)
        verify(jwtService, never()).generateToken(anyUsuario(), anyBoolean())
    }
}
