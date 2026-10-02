package com.ar.edu.unq.unqlassroom.integration.github.client

import com.ar.edu.unq.unqlassroom.exception.UnauthorizedException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate

@ExtendWith(MockitoExtension::class)
class GitHubOAuthClientTest {

    @Mock
    private lateinit var restTemplate: RestTemplate

    private lateinit var gitHubOAuthClient: GitHubOAuthClient

    private val clientId = "test-client-id"
    private val clientSecret = "test-client-secret"
    private val tokenUrl = "https://github.com/login/oauth/access_token"
    private val userUrl = "https://api.github.com/user"

    @BeforeEach
    fun setUp() {
        gitHubOAuthClient = GitHubOAuthClient(
            restTemplate = restTemplate,
            clientId = clientId,
            clientSecret = clientSecret,
            tokenUrl = tokenUrl,
            userUrl = userUrl
        )
    }

    @Test
    fun `intercambiarCodePorToken returns access token on success`() {
        val code = "valid-code"
        val expectedToken = "gho_test_token_123"
        val tokenResponse = GitHubOAuthTokenResponse(
            access_token = expectedToken,
            token_type = "bearer",
            scope = "read:user"
        )
        val responseEntity = ResponseEntity(tokenResponse, HttpStatus.OK)

        `when`(
            restTemplate.postForEntity(
                eq(tokenUrl),
                any(HttpEntity::class.java),
                eq(GitHubOAuthTokenResponse::class.java)
            )
        ).thenReturn(responseEntity)

        val result = gitHubOAuthClient.intercambiarCodePorToken(code)

        assertEquals(expectedToken, result)
    }

    @Test
    fun `intercambiarCodePorToken throws UnauthorizedException when restTemplate throws`() {
        val code = "bad-code"

        `when`(
            restTemplate.postForEntity(
                eq(tokenUrl),
                any(HttpEntity::class.java),
                eq(GitHubOAuthTokenResponse::class.java)
            )
        ).thenThrow(RestClientException("Connection error"))

        val exception = assertThrows<UnauthorizedException> {
            gitHubOAuthClient.intercambiarCodePorToken(code)
        }

        assertTrue(exception.message!!.contains("Error de comunicación con GitHub OAuth"))
    }

    @Test
    fun `intercambiarCodePorToken throws UnauthorizedException with error_description when token is missing`() {
        val code = "expired-code"
        val tokenResponse = GitHubOAuthTokenResponse(
            access_token = null,
            error = "bad_verification_code",
            error_description = "The code passed is incorrect or has expired"
        )
        val responseEntity = ResponseEntity(tokenResponse, HttpStatus.OK)

        `when`(
            restTemplate.postForEntity(
                eq(tokenUrl),
                any(HttpEntity::class.java),
                eq(GitHubOAuthTokenResponse::class.java)
            )
        ).thenReturn(responseEntity)

        val exception = assertThrows<UnauthorizedException> {
            gitHubOAuthClient.intercambiarCodePorToken(code)
        }

        assertTrue(exception.message!!.contains("The code passed is incorrect or has expired"))
    }

    @Test
    fun `intercambiarCodePorToken throws UnauthorizedException with error when error_description is null`() {
        val code = "bad-code"
        val tokenResponse = GitHubOAuthTokenResponse(
            access_token = "",
            error = "bad_verification_code",
            error_description = null
        )
        val responseEntity = ResponseEntity(tokenResponse, HttpStatus.OK)

        `when`(
            restTemplate.postForEntity(
                eq(tokenUrl),
                any(HttpEntity::class.java),
                eq(GitHubOAuthTokenResponse::class.java)
            )
        ).thenReturn(responseEntity)

        val exception = assertThrows<UnauthorizedException> {
            gitHubOAuthClient.intercambiarCodePorToken(code)
        }

        assertTrue(exception.message!!.contains("bad_verification_code"))
    }

    @Test
    fun `intercambiarCodePorToken throws UnauthorizedException default message when body is empty`() {
        val code = "code-no-body"
        val tokenResponse = GitHubOAuthTokenResponse()
        val responseEntity = ResponseEntity(tokenResponse, HttpStatus.OK)

        `when`(
            restTemplate.postForEntity(
                eq(tokenUrl),
                any(HttpEntity::class.java),
                eq(GitHubOAuthTokenResponse::class.java)
            )
        ).thenReturn(responseEntity)

        val exception = assertThrows<UnauthorizedException> {
            gitHubOAuthClient.intercambiarCodePorToken(code)
        }

        assertTrue(exception.message!!.contains("Código de autorización inválido o expirado"))
    }

    @Test
    fun `obtenerPerfilGitHub returns profile on success`() {
        val accessToken = "test-token"
        val expectedProfile = GitHubUserProfileResponse(
            login = "thiago",
            id = 12345L,
            name = "Thiago",
            email = "thiago@example.com",
            avatar_url = "https://avatar.url"
        )
        val responseEntity = ResponseEntity(expectedProfile, HttpStatus.OK)

        `when`(
            restTemplate.exchange(
                eq(userUrl),
                eq(HttpMethod.GET),
                any(HttpEntity::class.java),
                eq(GitHubUserProfileResponse::class.java)
            )
        ).thenReturn(responseEntity)

        val result = gitHubOAuthClient.obtenerPerfilGitHub(accessToken)

        assertEquals("thiago", result.login)
        assertEquals(12345L, result.id)
        assertEquals("Thiago", result.name)
        assertEquals("thiago@example.com", result.email)
        assertEquals("https://avatar.url", result.avatar_url)
    }

    @Test
    fun `obtenerPerfilGitHub throws UnauthorizedException when restTemplate throws`() {
        val accessToken = "invalid-token"

        `when`(
            restTemplate.exchange(
                eq(userUrl),
                eq(HttpMethod.GET),
                any(HttpEntity::class.java),
                eq(GitHubUserProfileResponse::class.java)
            )
        ).thenThrow(RestClientException("Unauthorized"))

        val exception = assertThrows<UnauthorizedException> {
            gitHubOAuthClient.obtenerPerfilGitHub(accessToken)
        }

        assertTrue(exception.message!!.contains("Error al consultar el perfil del usuario en GitHub"))
    }

    @Test
    fun `obtenerPerfilGitHub throws UnauthorizedException when body is null`() {
        val accessToken = "token"
        val responseEntity = ResponseEntity<GitHubUserProfileResponse>(null, HttpStatus.OK)

        `when`(
            restTemplate.exchange(
                eq(userUrl),
                eq(HttpMethod.GET),
                any(HttpEntity::class.java),
                eq(GitHubUserProfileResponse::class.java)
            )
        ).thenReturn(responseEntity)

        val exception = assertThrows<UnauthorizedException> {
            gitHubOAuthClient.obtenerPerfilGitHub(accessToken)
        }

        assertEquals("GitHub no retornó información del usuario", exception.message)
    }
}
