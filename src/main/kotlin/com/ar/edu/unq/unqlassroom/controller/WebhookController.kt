package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.service.WebhookIngestionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/webhooks")
class WebhookController(
    private val webhookIngestionService: WebhookIngestionService,
) {

    @PostMapping("/github")
    fun recibirWebhookGithub(
        @RequestHeader(value = "X-Hub-Signature-256", required = false) signature: String?,
        @RequestHeader(value = "X-GitHub-Event", defaultValue = "unknown") eventType: String,
        @RequestHeader(value = "X-GitHub-Delivery", required = false) deliveryId: String?,
        @RequestBody payload: String,
    ): ResponseEntity<Void> {
        webhookIngestionService.registrarEvento(payload, signature, eventType, deliveryId)
        return ResponseEntity.ok().build()
    }
}
