package com.ar.edu.unq.unqlassroom.util

import com.ar.edu.unq.unqlassroom.exception.InvalidWebhookSignatureException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WebhookSignatureValidatorTest {

    private val secret = "test-secret-123"
    private val validator = WebhookSignatureValidator(secret)

    private fun computeSignature(payload: String, key: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val hash = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        return "sha256=" + hash.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `isValid retorna true cuando la firma coincide exactamente con el secreto`() {
        val payload = "{\"action\":\"completed\",\"zen\":\"Practicality beats purity.\"}"
        val signature = computeSignature(payload, secret)

        val result = validator.isValid(payload, signature)

        assertTrue(result)
    }

    @Test
    fun `isValid retorna false cuando la firma no coincide`() {
        val payload = "{\"action\":\"completed\"}"
        val signature = computeSignature(payload, "wrong-secret")

        val result = validator.isValid(payload, signature)

        assertFalse(result)
    }

    @Test
    fun `isValid retorna false cuando el payload fue manipulado`() {
        val originalPayload = "{\"action\":\"completed\"}"
        val signature = computeSignature(originalPayload, secret)
        val tamperedPayload = "{\"action\":\"hacked\"}"

        val result = validator.isValid(tamperedPayload, signature)

        assertFalse(result)
    }

    @Test
    fun `isValid retorna false cuando el header es nulo o no empieza con sha256=`() {
        val payload = "{\"action\":\"completed\"}"

        assertFalse(validator.isValid(payload, null))
        assertFalse(validator.isValid(payload, ""))
        assertFalse(validator.isValid(payload, "invalid_header_format"))
    }

    @Test
    fun `isValid retorna false cuando el secreto esta en blanco`() {
        val emptySecretValidator = WebhookSignatureValidator("")
        val payload = "{\"action\":\"completed\"}"
        val signature = computeSignature(payload, "any-key")

        assertFalse(emptySecretValidator.isValid(payload, signature))
    }

    @Test
    fun `validate no lanza excepcion cuando la firma es valida`() {
        val payload = "{\"action\":\"completed\"}"
        val signature = computeSignature(payload, secret)

        assertDoesNotThrow {
            validator.validate(payload, signature)
        }
    }

    @Test
    fun `validate lanza InvalidWebhookSignatureException cuando la firma es invalida`() {
        val payload = "{\"action\":\"completed\"}"
        val signature = "sha256=invalidhash"

        val ex = assertThrows<InvalidWebhookSignatureException> {
            validator.validate(payload, signature)
        }
        assertEquals("Firma HMAC de Webhook inválida o ausente", ex.message)
    }
}
