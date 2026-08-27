package com.ar.edu.unq.unqlassroom.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64

@Component
class GitHubAppClient(
    private val properties: GitHubAppProperties,
    private val objectMapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
) {
    private val httpClient: HttpClient = HttpClient.newHttpClient()

    fun createInstallationToken(): GitHubInstallationTokenResponse {
        val jwt = createAppJwt()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("${properties.apiBaseUrl}/app/installations/${requiredInstallationId()}/access_tokens"))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Authorization", "Bearer $jwt")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "GitHub installation token request failed with status ${response.statusCode()}: ${response.body()}",
            )
        }

        return objectMapper.readValue(response.body(), GitHubInstallationTokenResponse::class.java)
    }

    fun <T> executeInstallationRequest(
        method: String,
        path: String,
        responseType: Class<T>,
        body: String? = null,
    ): T {
        val token = createInstallationToken().token
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create("${properties.apiBaseUrl}/${path.trimStart('/')}") )
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("Authorization", "Bearer $token")

        val request = when (method.uppercase()) {
            "GET" -> requestBuilder.GET().build()
            "POST" -> requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body.orEmpty(), StandardCharsets.UTF_8)).build()
            "PATCH" -> requestBuilder.method("PATCH", HttpRequest.BodyPublishers.ofString(body.orEmpty(), StandardCharsets.UTF_8)).build()
            "PUT" -> requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body.orEmpty(), StandardCharsets.UTF_8)).build()
            "DELETE" -> requestBuilder.DELETE().build()
            else -> requestBuilder.method(
                method.uppercase(),
                if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8),
            ).build()
        }

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw IllegalStateException(
                "GitHub API request failed with status ${response.statusCode()}: ${response.body()}",
            )
        }

        return objectMapper.readValue(response.body(), responseType)
    }

    private fun createAppJwt(): String {
        val issuedAt = Instant.now().epochSecond - 60
        val expiresAt = issuedAt + 600
        val header = base64UrlEncode(("{" + "\"alg\":\"RS256\",\"typ\":\"JWT\"" + "}").toByteArray(StandardCharsets.UTF_8))
        val payload = base64UrlEncode(
            ("{" +
                "\"iat\":$issuedAt," +
                "\"exp\":$expiresAt," +
                "\"iss\":${requiredAppId()}" +
                "}").toByteArray(StandardCharsets.UTF_8),
        )
        val signingInput = "$header.$payload"
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(loadPrivateKey())
        signer.update(signingInput.toByteArray(StandardCharsets.UTF_8))
        val signature = base64UrlEncode(signer.sign())
        return "$header.$payload.$signature"
    }

    private fun loadPrivateKey(): PrivateKey {
        val pemPath = requiredPrivateKeyPath()
        val pem = Files.readString(Path.of(pemPath))
        val base64Key = pem.lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
            .replace("\\s+".toRegex(), "")

        val keyBytes = Base64.getDecoder().decode(base64Key)
        val keyFactory = KeyFactory.getInstance("RSA")

        return try {
            keyFactory.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
        } catch (_: InvalidKeySpecException) {
            keyFactory.generatePrivate(PKCS8EncodedKeySpec(wrapPkcs1KeyInPkcs8(keyBytes)))
        }
    }

    private fun wrapPkcs1KeyInPkcs8(pkcs1Key: ByteArray): ByteArray {
        val algorithmIdentifier = byteArrayOf(
            0x30, 0x0d,
            0x06, 0x09,
            0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01,
            0x05, 0x00,
        )
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val privateKey = derEncode(0x04, pkcs1Key)
        return derEncode(0x30, version + algorithmIdentifier + privateKey)
    }

    private fun derEncode(tag: Int, value: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        output.write(tag)
        writeDerLength(output, value.size)
        output.write(value)
        return output.toByteArray()
    }

    private fun writeDerLength(output: ByteArrayOutputStream, length: Int) {
        if (length < 0x80) {
            output.write(length)
            return
        }

        val bytes = length.toBigInteger().toByteArray()
        output.write(0x80 or bytes.size)
        output.write(bytes)
    }

    private fun base64UrlEncode(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun requiredAppId(): Long = properties.appId.trim().toLongOrNull()
        ?: throw IllegalStateException("github.app.app-id must be configured with the GitHub App id")

    private fun requiredInstallationId(): Long = properties.installationId.trim().toLongOrNull()
        ?: throw IllegalStateException("github.app.installation-id must be configured with the installation id")

    private fun requiredPrivateKeyPath(): String = properties.privateKeyPath.trim().takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("github.app.private-key-path must point to the PEM file in the project root")
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubInstallationTokenResponse(
    @JsonProperty("token") val token: String = "",
    @JsonProperty("expires_at") val expiresAt: String = "",
    @JsonProperty("permissions") val permissions: Map<String, String>? = null,
    @JsonProperty("repository_selection") val repositorySelection: String? = null,
)