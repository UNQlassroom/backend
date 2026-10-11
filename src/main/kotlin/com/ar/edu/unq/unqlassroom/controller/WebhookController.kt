package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEventStatus
import com.ar.edu.unq.unqlassroom.repository.WebhookEventRepository
import com.ar.edu.unq.unqlassroom.util.WebhookSignatureValidator
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/webhooks")
class WebhookController(
    private val webhookSignatureValidator: WebhookSignatureValidator,
    private val webhookEventRepository: WebhookEventRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostMapping("/github")
    fun recibirWebhookGithub(
        @RequestHeader(value = "X-Hub-Signature-256", required = false) signature: String?,
        @RequestHeader(value = "X-GitHub-Event", defaultValue = "unknown") eventType: String,
        @RequestBody payload: String,
    ): ResponseEntity<Void> {
        val valido = webhookSignatureValidator.isValid(payload, signature)
        if (!valido) {
            logger.warn("Recepción de Webhook rechazada: firma HMAC inválida. Evento: {}", eventType)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val evento = WebhookEvent(
            payload = payload,
            eventType = eventType,
            status = WebhookEventStatus.PENDING,
        )
        webhookEventRepository.save(evento)
        logger.info("Webhook recibido y persistido en Inbox con estado PENDING. ID: {}, Evento: {}", evento.id, eventType)

        return ResponseEntity.ok().build()
    }
}
