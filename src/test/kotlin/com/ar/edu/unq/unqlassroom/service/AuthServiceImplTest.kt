package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.GitHubLoginRequestDTO
import com.ar.edu.unq.unqlassroom.github.GitHubOAuthClient
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

    @InjectMocks
    private lateinit var authService: AuthServiceImpl

    private fun anyUsuario(): Usuario {
        Mockito.any(Usuario::class.java)
        return Usuario(username = "")
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
        assertEquals("alumno_nuevo", response.user.username)
        assertFalse(response.user.esDocente)
        assertEquals("Alumno Nuevo", response.user.nombreCompleto)
        assertEquals("alumno@unq.edu.ar", response.user.email)
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
        assertTrue(response.user.esDocente)
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
        assertFalse(response.user.esDocente)
        assertEquals("profe.actualizado@unq.edu.ar", existingDocente.email)
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
}
