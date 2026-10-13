package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEventStatus
import com.ar.edu.unq.unqlassroom.repository.WebhookEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WebhookIngestionService(
    private val webhookEventRepository: WebhookEventRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun registrarEvento(payload: String, eventType: String): WebhookEvent {
        val evento = WebhookEvent(
            payload = payload,
            eventType = eventType,
            status = WebhookEventStatus.PENDING,
        )
        val eventoGuardado = webhookEventRepository.save(evento)
        logger.info(
            "Webhook recibido y persistido en Inbox con estado PENDING. ID: {}, Evento: {}",
            eventoGuardado.id,
            eventType
        )
        return eventoGuardado
    }
}
