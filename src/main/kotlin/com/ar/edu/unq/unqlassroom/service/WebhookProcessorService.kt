package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEventStatus
import com.ar.edu.unq.unqlassroom.repository.WebhookEventRepository
import com.ar.edu.unq.unqlassroom.service.webhook.WebhookEventHandler
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class WebhookProcessorService(
    private val webhookEventRepository: WebhookEventRepository,
    private val handlers: List<WebhookEventHandler> = emptyList(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${github.webhook.cron-interval-ms:10000}")
    @Transactional
    fun procesarEventosPendientes() {
        val pendientes = webhookEventRepository.findByStatusForUpdate(
            WebhookEventStatus.PENDING,
            PageRequest.of(0, 50)
        )

        if (pendientes.isEmpty()) {
            return
        }

        logger.info("Iniciando procesamiento de {} eventos de webhook pendientes", pendientes.size)

        for (evento in pendientes) {
            try {
                procesarEventoIndividual(evento)
            } catch (e: Exception) {
                logger.error("Error inesperado procesando evento ${evento.id}", e)
            }
        }
    }

    fun procesarEventoIndividual(evento: WebhookEvent) {
        try {
            ejecutarLogicaDeDominio(evento)
            evento.marcarProcesado()
            logger.info("Evento de webhook {} procesado con éxito", evento.id)
        } catch (e: Exception) {
            evento.marcarError(e.message ?: "Error desconocido al procesar webhook")
            logger.error("Fallo al procesar evento {}: {}", evento.id, e.message)
        } finally {
            webhookEventRepository.save(evento)
        }
    }

    fun ejecutarLogicaDeDominio(evento: WebhookEvent) {
        val handler = handlers.firstOrNull { it.canHandle(evento) }
        if (handler != null) {
            handler.handle(evento)
        } else {
            logger.info(
                "Evento genérico '{}' procesado sin acción de dominio específica (sin handler registrado)",
                evento.eventType
            )
        }
    }
}
