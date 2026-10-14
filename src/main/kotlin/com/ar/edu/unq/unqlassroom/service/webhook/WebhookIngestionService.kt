package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEventStatus
import com.ar.edu.unq.unqlassroom.repository.WebhookEventRepository
import com.ar.edu.unq.unqlassroom.util.WebhookSignatureValidator
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WebhookIngestionService(
    private val webhookEventRepository: WebhookEventRepository,
    private val webhookSignatureValidator: WebhookSignatureValidator,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun registrarEvento(payload: String, signature: String?, eventType: String, deliveryId: String? = null) {
        webhookSignatureValidator.validate(payload, signature)

        if (esDuplicado(deliveryId)) {
            logger.warn("Webhook duplicado ignorado. Delivery ID: {}", deliveryId)
            return
        }

        val action = extraerAction(payload)

        val evento = WebhookEvent(
            deliveryId = deliveryId,
            eventType = eventType,
            action = action,
            payload = payload,
            status = WebhookEventStatus.PENDING,
        )

        val eventoGuardado = webhookEventRepository.save(evento)
        logger.info(
            "Webhook recibido y persistido en Inbox con estado PENDING. ID: {}, Delivery ID: {}, Evento: {}, Action: {}",
            eventoGuardado.id,
            deliveryId,
            eventType,
            action
        )
    }

    private fun esDuplicado(deliveryId: String?): Boolean =
        deliveryId != null && webhookEventRepository.existsByDeliveryId(deliveryId)

    private fun extraerAction(payload: String): String? {
        return try {
            val node = objectMapper.readTree(payload).get("action")
            if (node != null && !node.isNull) node.asText() else null
        } catch (_: Exception) {
            null
        }
    }
}
