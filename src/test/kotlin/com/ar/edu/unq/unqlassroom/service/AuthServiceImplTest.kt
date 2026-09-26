package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.GitHubLoginRequestDTO
import com.ar.edu.unq.unqlassroom.github.GitHubAppProperties
import com.ar.edu.unq.unqlassroom.github.GitHubOAuthClient
import com.ar.edu.unq.unqlassroom.github.GitHubOrgMembershipResponse
import com.ar.edu.unq.unqlassroom.github.GitHubOrgService
import com.ar.edu.unq.unqlassroom.github.GitHubUserProfileResponse
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.impl.AuthServiceImpl
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
                esDocente = u.esDocente,
                email = u.email,
                nombreCompleto = u.nombreCompleto
            )
        }
        `when`(jwtService.generateToken(anyUsuario())).thenReturn("jwt-token-xyz")

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertEquals("jwt-token-xyz", response.token)
        assertEquals("alumno_nuevo", response.user?.username)
        assertEquals(false, response.user?.esDocente)
        assertEquals("Alumno Nuevo", response.user?.nombreCompleto)
        assertEquals("alumno@unq.edu.ar", response.user?.email)
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
                esDocente = u.esDocente,
                email = u.email,
                nombreCompleto = u.nombreCompleto
            )
        }
        `when`(jwtService.generateToken(anyUsuario())).thenReturn("jwt-docente-token")

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertEquals("jwt-docente-token", response.token)
        assertEquals(true, response.user?.esDocente)
    }

    @Test
    fun `loginConGitHub does not preserves existing role for existing user`() {
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
            esDocente = true,
            email = "profe.viejo@unq.edu.ar",
            nombreCompleto = "Profesor"
        )

        `when`(gitHubOAuthClient.intercambiarCodePorToken("oauth-code-existing")).thenReturn("gho_token_existing")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_token_existing")).thenReturn(profile)
        `when`(usuarioRepository.findByUsername("profe_existente")).thenReturn(existingDocente)
        `when`(usuarioRepository.save(existingDocente)).thenReturn(existingDocente)
        `when`(jwtService.generateToken(existingDocente)).thenReturn("jwt-token-docente")

        val response = authService.loginConGitHub(dto)

        assertEquals("jwt-token-docente", response.token)
        assertEquals(false, response.user?.esDocente)
        assertEquals("profe.actualizado@unq.edu.ar", existingDocente.email)
        verifyNoInteractions(gitHubOrgService)
    }

    @Test
    fun `loadUserByUsername returns UserDetails with correct authority`() {
        val docente = Usuario(id = 1L, username = "docente1", esDocente = true)
        val alumno = Usuario(id = 2L, username = "alumno1", esDocente = false)

        `when`(usuarioRepository.findByUsername("docente1")).thenReturn(docente)
        `when`(usuarioRepository.findByUsername("alumno1")).thenReturn(alumno)

        val userDetailsDocente = authService.loadUserByUsername("docente1")
        val userDetailsAlumno = authService.loadUserByUsername("alumno1")

        assertEquals("docente1", userDetailsDocente.username)
        assertTrue(userDetailsDocente.authorities.any { it.authority == "ROLE_DOCENTE" })

        assertEquals("alumno1", userDetailsAlumno.username)
        assertTrue(userDetailsAlumno.authorities.any { it.authority == "ROLE_ALUMNO" })
    }

    @Test
    fun `loadUserByUsername throws UsernameNotFoundException when user does not exist`() {
        `when`(usuarioRepository.findByUsername("desconocido")).thenReturn(null)

        assertThrows<UsernameNotFoundException> {
            authService.loadUserByUsername("desconocido")
        }
    }

    @Test
    fun `loginConGitHub invites user and returns redirect response when user is not member of org`() {
        val dto = GitHubLoginRequestDTO(code = "code-no-org", esDocente = false)
        val profile = GitHubUserProfileResponse(
            login = "usuario_sin_org",
            id = 555L,
            name = "Sin Org",
            email = "sinorg@unq.edu.ar",
            avatar_url = null
        )

        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(gitHubOAuthClient.intercambiarCodePorToken("code-no-org")).thenReturn("gho_token_no_org")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_token_no_org")).thenReturn(profile)
        `when`(gitHubOrgService.obtenerMembresia("usuario_sin_org")).thenReturn(null)
        `when`(gitHubOrgService.invitarMiembro(username = "usuario_sin_org", role = "member"))
            .thenReturn(GitHubOrgMembershipResponse(state = "pending", role = "member"))

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertTrue(response.requiereUnirseAOrg)
        assertEquals("https://github.com/orgs/UNQlassroom/invitation", response.redirectUrl)
        assertNull(response.token)
        assertNull(response.user)

        verify(gitHubOrgService).invitarMiembro(username = "usuario_sin_org", role = "member")
        verify(usuarioRepository, never()).save(anyUsuario())
        verify(jwtService, never()).generateToken(anyUsuario())
    }

    @Test
    fun `loginConGitHub invites docente with admin role when docente is not member of org`() {
        val dto = GitHubLoginRequestDTO(code = "code-docente-no-org", esDocente = true)
        val profile = GitHubUserProfileResponse(
            login = "docente_sin_org",
            id = 666L,
            name = "Docente Sin Org",
            email = "docentesinorg@unq.edu.ar",
            avatar_url = null
        )

        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(gitHubOAuthClient.intercambiarCodePorToken("code-docente-no-org")).thenReturn("gho_token_doc_no_org")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_token_doc_no_org")).thenReturn(profile)
        `when`(gitHubOrgService.obtenerMembresia("docente_sin_org")).thenReturn(null)
        `when`(gitHubOrgService.invitarMiembro(username = "docente_sin_org", role = "admin"))
            .thenReturn(GitHubOrgMembershipResponse(state = "pending", role = "admin"))

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertTrue(response.requiereUnirseAOrg)
        assertEquals("https://github.com/orgs/UNQlassroom/invitation", response.redirectUrl)
        assertNull(response.token)
        assertNull(response.user)

        verify(gitHubOrgService).invitarMiembro(username = "docente_sin_org", role = "admin")
        verify(usuarioRepository, never()).save(anyUsuario())
        verify(jwtService, never()).generateToken(anyUsuario())
    }

    @Test
    fun `loginConGitHub returns redirect response without inviting when user already has pending invitation`() {
        val dto = GitHubLoginRequestDTO(code = "code-pending-org", esDocente = false)
        val profile = GitHubUserProfileResponse(
            login = "usuario_pending",
            id = 777L,
            name = "Usuario Pending",
            email = "pending@unq.edu.ar",
            avatar_url = null
        )

        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(gitHubOAuthClient.intercambiarCodePorToken("code-pending-org")).thenReturn("gho_token_pending")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_token_pending")).thenReturn(profile)
        `when`(gitHubOrgService.obtenerMembresia("usuario_pending"))
            .thenReturn(GitHubOrgMembershipResponse(state = "pending", role = "member"))

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertTrue(response.requiereUnirseAOrg)
        assertEquals("https://github.com/orgs/UNQlassroom/invitation", response.redirectUrl)
        assertNull(response.token)
        assertNull(response.user)

        verify(gitHubOrgService).obtenerMembresia("usuario_pending")
        verifyNoMoreInteractions(gitHubOrgService)
        verify(usuarioRepository, never()).save(anyUsuario())
        verify(jwtService, never()).generateToken(anyUsuario())
    }

    @Test
    fun `loginConGitHub succeeds and generates token when user is active member of org`() {
        val dto = GitHubLoginRequestDTO(code = "code-active-org", esDocente = false)
        val profile = GitHubUserProfileResponse(
            login = "usuario_activo",
            id = 888L,
            name = "Usuario Activo",
            email = "activo@unq.edu.ar",
            avatar_url = null
        )

        `when`(properties.organization).thenReturn("UNQlassroom")
        `when`(gitHubOAuthClient.intercambiarCodePorToken("code-active-org")).thenReturn("gho_token_activo")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_token_activo")).thenReturn(profile)
        `when`(gitHubOrgService.obtenerMembresia("usuario_activo"))
            .thenReturn(GitHubOrgMembershipResponse(state = "active", role = "member"))
        `when`(usuarioRepository.findByUsername("usuario_activo")).thenReturn(null)
        `when`(usuarioRepository.save(anyUsuario())).thenAnswer { invocation ->
            val u = invocation.getArgument<Usuario>(0)
            Usuario(
                id = 30L,
                username = u.username,
                esDocente = u.esDocente,
                email = u.email,
                nombreCompleto = u.nombreCompleto
            )
        }
        `when`(jwtService.generateToken(anyUsuario())).thenReturn("jwt-token-activo")

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertFalse(response.requiereUnirseAOrg)
        assertNull(response.redirectUrl)
        assertEquals("jwt-token-activo", response.token)
        assertEquals("usuario_activo", response.user?.username)
        verify(gitHubOrgService).obtenerMembresia("usuario_activo")
        verifyNoMoreInteractions(gitHubOrgService)
    }

    @Test
    fun `loginConGitHub does not check org membership when user already exists in the app even if org is configured`() {
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
            esDocente = false,
            email = "existente@unq.edu.ar",
            nombreCompleto = "Usuario Existente"
        )

        Mockito.lenient().`when`(properties.organization).thenReturn("UNQlassroom")
        `when`(gitHubOAuthClient.intercambiarCodePorToken("oauth-code-existing")).thenReturn("gho_existing")
        `when`(gitHubOAuthClient.obtenerPerfilGitHub("gho_existing")).thenReturn(profile)
        `when`(usuarioRepository.findByUsername("usuario_existente")).thenReturn(existingUser)
        `when`(usuarioRepository.save(existingUser)).thenReturn(existingUser)
        `when`(jwtService.generateToken(existingUser)).thenReturn("jwt-existing")

        val response = authService.loginConGitHub(dto)

        assertNotNull(response)
        assertEquals("jwt-existing", response.token)
        assertEquals(false, response.requiereUnirseAOrg)
        assertNull(response.redirectUrl)
        verifyNoInteractions(gitHubOrgService)
        verify(properties, never()).organization
    }
}
