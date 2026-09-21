package com.ar.edu.unq.unqlassroom.github

import com.ar.edu.unq.unqlassroom.errors.UnauthorizedException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

data class GitHubOAuthTokenResponse(
    val access_token: String? = null,
    val token_type: String? = null,
    val scope: String? = null,
    val error: String? = null,
    val error_description: String? = null
)

data class GitHubUserProfileResponse(
    val login: String,
    val id: Long,
    val name: String? = null,
    val email: String? = null,
    val avatar_url: String? = null
)

@Component
class GitHubOAuthClient(
    private val restTemplate: RestTemplate,

    @Value("\${github.oauth.client-id:}")
    private val clientId: String,

    @Value("\${github.oauth.client-secret:}")
    private val clientSecret: String,

    @Value("\${github.oauth.token-url:https://github.com/login/oauth/access_token}")
    private val tokenUrl: String,

    @Value("\${github.oauth.user-url:https://api.github.com/user}")
    private val userUrl: String
) {

    fun intercambiarCodePorToken(code: String): String {
        val headers = HttpHeaders().apply {
            accept = listOf(MediaType.APPLICATION_JSON)
            contentType = MediaType.APPLICATION_JSON
        }

        val body = mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "code" to code
        )

        val request = HttpEntity(body, headers)
        val response = try {
            restTemplate.postForEntity(tokenUrl, request, GitHubOAuthTokenResponse::class.java)
        } catch (e: Exception) {
            throw UnauthorizedException("Error de comunicación con GitHub OAuth: ${e.message}")
        }

        val tokenResponse = response.body
        if (tokenResponse?.access_token.isNullOrBlank()) {
            val errorMsg = tokenResponse?.error_description ?: tokenResponse?.error ?: "Código de autorización inválido o expirado"
            throw UnauthorizedException("No se pudo obtener el token de acceso de GitHub: $errorMsg")
        }

        return tokenResponse!!.access_token!!
    }

    fun obtenerPerfilGitHub(accessToken: String): GitHubUserProfileResponse {
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
            set("Accept", "application/vnd.github+json")
        }

        val request = HttpEntity<Void>(headers)
        val response = try {
            restTemplate.exchange(userUrl, HttpMethod.GET, request, GitHubUserProfileResponse::class.java)
        } catch (e: Exception) {
            throw UnauthorizedException("Error al consultar el perfil del usuario en GitHub: ${e.message}")
        }

        return response.body ?: throw UnauthorizedException("GitHub no retornó información del usuario")
    }
}
