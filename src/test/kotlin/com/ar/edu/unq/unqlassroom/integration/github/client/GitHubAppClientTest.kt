package com.ar.edu.unq.unqlassroom.integration.github.client

import com.ar.edu.unq.unqlassroom.integration.github.config.GitHubAppProperties
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.util.Base64

class GitHubAppClientTest {

    private lateinit var server: HttpServer
    private lateinit var properties: GitHubAppProperties
    private lateinit var client: GitHubAppClient
    private var port: Int = 0

    private val appId = "12345"
    private val installationId = "67890"
    private lateinit var tempPemFile: Path

    @BeforeEach
    fun setUp() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val pkcs8Bytes = kp.private.encoded
        val base64Key = Base64.getEncoder().encodeToString(pkcs8Bytes)
        val pemContent = "-----BEGIN PRIVATE KEY-----\n$base64Key\n-----END PRIVATE KEY-----"
        tempPemFile = Files.createTempFile("test_rsa_key", ".pem")
        Files.writeString(tempPemFile, pemContent)

        server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
        port = server.address.port
        server.start()

        properties = GitHubAppProperties(
            appId = appId,
            installationId = installationId,
            privateKeyPath = tempPemFile.toAbsolutePath().toString(),
            apiBaseUrl = "http://localhost:$port"
        )
        client = GitHubAppClient(properties)
    }

    @AfterEach
    fun tearDown() {
        server.stop(0)
        Files.deleteIfExists(tempPemFile)
    }

    private fun respondJson(exchange: HttpExchange, status: Int, json: String) {
        val bytes = json.toByteArray(StandardCharsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
        exchange.responseBody.close()
    }

    private fun respondEmpty(exchange: HttpExchange, status: Int) {
        exchange.sendResponseHeaders(status, -1)
        exchange.responseBody.close()
    }

    @Test
    fun `getInstallationToken caches token when valid`() {
        var tokenCalls = 0
        server.createContext("/app/installations/$installationId/access_tokens") { exchange ->
            tokenCalls++
            respondJson(exchange, 201, """{"token":"token_cached_123","expires_at":"2099-01-01T00:00:00Z"}""")
        }

        val token1 = client.getInstallationToken()
        val token2 = client.getInstallationToken()

        assertEquals("token_cached_123", token1)
        assertEquals("token_cached_123", token2)
        assertEquals(1, tokenCalls)
    }

    @Test
    fun `getInstallationToken refreshes token when expired or invalid format`() {
        var tokenCalls = 0
        server.createContext("/app/installations/$installationId/access_tokens") { exchange ->
            tokenCalls++
            if (tokenCalls == 1) {
                // Return already expired date
                respondJson(exchange, 201, """{"token":"token_expired","expires_at":"2000-01-01T00:00:00Z"}""")
            } else {
                respondJson(exchange, 201, """{"token":"token_fresh","expires_at":"2099-01-01T00:00:00Z"}""")
            }
        }

        val token1 = client.getInstallationToken()
        assertEquals("token_expired", token1)

        val token2 = client.getInstallationToken()
        assertEquals("token_fresh", token2)
        assertEquals(2, tokenCalls)
    }

    @Test
    fun `createInstallationToken throws IllegalStateException when response status is not 2xx`() {
        server.createContext("/app/installations/$installationId/access_tokens") { exchange ->
            respondJson(exchange, 401, """{"message":"Bad credentials"}""")
        }

        val exception = assertThrows<IllegalStateException> {
            client.createInstallationToken()
        }

        assertTrue(exception.message!!.contains("GitHub installation token request failed with status 401"))
    }

    @Test
    fun `executeInstallationRequest executes GET, POST, PATCH, PUT, DELETE successfully`() {
        // Mock token endpoint
        server.createContext("/app/installations/$installationId/access_tokens") { exchange ->
            respondJson(exchange, 200, """{"token":"test-token-xyz","expires_at":"2099-01-01T00:00:00Z"}""")
        }

        // Mock test endpoints
        server.createContext("/test-get") { exchange ->
            assertEquals("GET", exchange.requestMethod)
            assertEquals("Bearer test-token-xyz", exchange.requestHeaders.getFirst("Authorization"))
            respondJson(exchange, 200, """{"token":"success-get"}""")
        }
        server.createContext("/test-post") { exchange ->
            assertEquals("POST", exchange.requestMethod)
            val body = String(exchange.requestBody.readAllBytes(), StandardCharsets.UTF_8)
            assertEquals("""{"key":"val"}""", body)
            respondJson(exchange, 200, """{"token":"success-post"}""")
        }
        server.createContext("/test-patch") { exchange ->
            assertEquals("PATCH", exchange.requestMethod)
            respondJson(exchange, 200, """{"token":"success-patch"}""")
        }
        server.createContext("/test-put") { exchange ->
            assertEquals("PUT", exchange.requestMethod)
            respondJson(exchange, 200, """{"token":"success-put"}""")
        }
        server.createContext("/test-delete") { exchange ->
            assertEquals("DELETE", exchange.requestMethod)
            respondEmpty(exchange, 204)
        }

        val resGet = client.executeInstallationRequest("GET", "/test-get", GitHubInstallationTokenResponse::class.java)
        assertEquals("success-get", resGet.token)

        val resPost = client.executeInstallationRequest("POST", "test-post", GitHubInstallationTokenResponse::class.java, """{"key":"val"}""")
        assertEquals("success-post", resPost.token)

        val resPatch = client.executeInstallationRequest("PATCH", "/test-patch", GitHubInstallationTokenResponse::class.java, "{}")
        assertEquals("success-patch", resPatch.token)

        val resPut = client.executeInstallationRequest("PUT", "/test-put", GitHubInstallationTokenResponse::class.java, "{}")
        assertEquals("success-put", resPut.token)

        val resDelete = client.executeInstallationRequest("DELETE", "/test-delete", Unit::class.java)
        assertEquals(Unit, resDelete)
    }

    @Test
    fun `executeInstallationRequest handles blank response body and void types`() {
        server.createContext("/app/installations/$installationId/access_tokens") { exchange ->
            respondJson(exchange, 200, """{"token":"test-token","expires_at":"2099-01-01T00:00:00Z"}""")
        }

        server.createContext("/empty") { exchange ->
            respondEmpty(exchange, 200)
        }

        val unitRes = client.executeInstallationRequest("POST", "/empty", Unit::class.java)
        assertEquals(Unit, unitRes)

        val nullRes = client.executeInstallationRequest("POST", "/empty", String::class.java)
        assertNull(nullRes)
    }

    @Test
    fun `executeInstallationRequest with custom HTTP method and throws on failure`() {
        server.createContext("/app/installations/$installationId/access_tokens") { exchange ->
            respondJson(exchange, 200, """{"token":"test-token","expires_at":"2099-01-01T00:00:00Z"}""")
        }

        server.createContext("/custom-head") { exchange ->
            respondEmpty(exchange, 200)
        }

        server.createContext("/fail") { exchange ->
            respondJson(exchange, 500, """{"error":"internal"}""")
        }

        // Custom method branch in when(method)
        val customRes = client.executeInstallationRequest("HEAD", "/custom-head", Unit::class.java)
        assertEquals(Unit, customRes)

        val exception = assertThrows<IllegalStateException> {
            client.executeInstallationRequest("GET", "/fail", String::class.java)
        }
        assertTrue(exception.message!!.contains("GitHub API request failed with status 500"))
    }

    @Test
    fun `checkResourceExists returns true for 200, false for 404, and throws on other`() {
        server.createContext("/app/installations/$installationId/access_tokens") { exchange ->
            respondJson(exchange, 200, """{"token":"test-token","expires_at":"2099-01-01T00:00:00Z"}""")
        }

        server.createContext("/resource-exists") { exchange ->
            respondEmpty(exchange, 200)
        }
        server.createContext("/resource-not-found") { exchange ->
            respondEmpty(exchange, 404)
        }
        server.createContext("/resource-error") { exchange ->
            respondEmpty(exchange, 403)
        }

        assertTrue(client.checkResourceExists("/resource-exists"))
        assertFalse(client.checkResourceExists("/resource-not-found"))

        val exception = assertThrows<IllegalStateException> {
            client.checkResourceExists("/resource-error")
        }
        assertTrue(exception.message!!.contains("GitHub API request failed with status 403"))
    }

    @Test
    fun `userExists calls checkResourceExists for users path`() {
        server.createContext("/app/installations/$installationId/access_tokens") { exchange ->
            respondJson(exchange, 200, """{"token":"test-token","expires_at":"2099-01-01T00:00:00Z"}""")
        }

        server.createContext("/users/existing-user") { exchange ->
            respondEmpty(exchange, 200)
        }
        server.createContext("/users/missing-user") { exchange ->
            respondEmpty(exchange, 404)
        }

        assertTrue(client.userExists("existing-user"))
        assertFalse(client.userExists("missing-user"))
    }

    @Test
    fun `loadPrivateKey works with PKCS8 key file`() {
        // Generate a standard RSA PKCS#8 key pair
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val pkcs8Bytes = kp.private.encoded
        val base64Key = Base64.getEncoder().encodeToString(pkcs8Bytes)
        val pemContent = "-----BEGIN PRIVATE KEY-----\n$base64Key\n-----END PRIVATE KEY-----"

        val tempFile = Files.createTempFile("test_pkcs8", ".pem")
        Files.writeString(tempFile, pemContent)

        try {
            val pkcs8Props = GitHubAppProperties(
                appId = appId,
                installationId = installationId,
                privateKeyPath = tempFile.toAbsolutePath().toString(),
                apiBaseUrl = "http://localhost:$port"
            )
            val pkcs8Client = GitHubAppClient(pkcs8Props)

            server.createContext("/app/installations/$installationId/access_tokens") { exchange ->
                respondJson(exchange, 200, """{"token":"pkcs8-token","expires_at":"2099-01-01T00:00:00Z"}""")
            }

            val token = pkcs8Client.getInstallationToken()
            assertEquals("pkcs8-token", token)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `loadPrivateKey works with PKCS1 key file`() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()
        val pkcs8Bytes = kp.private.encoded
        // In RSA PKCS#8, the PKCS#1 DER key is contained inside the OCTET STRING at offset 26
        val pkcs1Bytes = pkcs8Bytes.copyOfRange(26, pkcs8Bytes.size)
        val base64Key = Base64.getEncoder().encodeToString(pkcs1Bytes)
        val pemContent = "-----BEGIN RSA PRIVATE KEY-----\n$base64Key\n-----END RSA PRIVATE KEY-----"

        val tempFile = Files.createTempFile("test_pkcs1", ".pem")
        Files.writeString(tempFile, pemContent)

        try {
            val pkcs1Props = GitHubAppProperties(
                appId = appId,
                installationId = installationId,
                privateKeyPath = tempFile.toAbsolutePath().toString(),
                apiBaseUrl = "http://localhost:$port"
            )
            val pkcs1Client = GitHubAppClient(pkcs1Props)

            server.createContext("/app/installations/$installationId/access_tokens") { exchange ->
                respondJson(exchange, 200, """{"token":"pkcs1-token","expires_at":"2099-01-01T00:00:00Z"}""")
            }

            val token = pkcs1Client.getInstallationToken()
            assertEquals("pkcs1-token", token)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    fun `missing or invalid configuration throws IllegalStateException`() {
        val clientMissingAppId = GitHubAppClient(properties.copy(appId = "not-a-number"))
        assertThrows<IllegalStateException> { clientMissingAppId.createInstallationToken() }

        val clientMissingInstId = GitHubAppClient(properties.copy(installationId = ""))
        assertThrows<IllegalStateException> { clientMissingInstId.createInstallationToken() }

        val clientMissingKeyPath = GitHubAppClient(properties.copy(privateKeyPath = "   "))
        assertThrows<IllegalStateException> { clientMissingKeyPath.createInstallationToken() }
    }
}
