package com.ar.edu.unq.unqlassroom.util

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Component
class WebhookSignatureValidator(
    @Value("\${github.webhook.secret:}")
    private val secret: String
) {
    fun isValid(payload: String, signatureHeader: String?): Boolean {
        if (secret.isBlank() || signatureHeader.isNullOrBlank()) {
            return false
        }

        if (!signatureHeader.startsWith("sha256=")) {
            return false
        }

        val expectedHash = signatureHeader.substringAfter("sha256=").trim()
        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)

        val computedHashBytes = mac.doFinal(payload.toByteArray(StandardCharsets.UTF_8))
        val computedHash = computedHashBytes.joinToString("") { "%02x".format(it) }

        return MessageDigest.isEqual(
            computedHash.toByteArray(StandardCharsets.UTF_8),
            expectedHash.toByteArray(StandardCharsets.UTF_8)
        )
    }
}
