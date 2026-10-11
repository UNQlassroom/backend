package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEventStatus
import com.ar.edu.unq.unqlassroom.repository.WebhookEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class WebhookProcessorService(
    private val webhookEventRepository: WebhookEventRepository,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${github.webhook.cron-interval-ms:10000}")
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
        val rootNode = objectMapper.readTree(evento.payload)

        when (evento.eventType.lowercase()) {
            "ping" -> {
                logger.info("Webhook ping recibido de GitHub. Zen: {}", rootNode.get("zen")?.asText())
            }
            "push" -> {
                val repoName = rootNode.path("repository").path("name").asText("desconocido")
                val pusher = rootNode.path("pusher").path("name").asText("desconocido")
                val commitsCount = rootNode.path("commits").size()
                logger.info(
                    "Simulando actualización de dominio: Push en repo '{}' por '{}' con {} commits",
                    repoName, pusher, commitsCount
                )
            }
            "workflow_run" -> {
                val conclusion = rootNode.path("workflow_run").path("conclusion").asText("desconocido")
                val repoName = rootNode.path("repository").path("name").asText("desconocido")
                logger.info(
                    "Simulando actualización de CI/CD: Workflow en '{}' con estado '{}'",
                    repoName, conclusion
                )
            }
            "release" -> {
                val tagName = rootNode.path("release").path("tag_name").asText("desconocido")
                val repoName = rootNode.path("repository").path("name").asText("desconocido")
                logger.info(
                    "Simulando registro de entrega por Release '{}' en repo '{}'",
                    tagName, repoName
                )
            }
            else -> {
                logger.info("Evento genérico '{}' procesado sin acción de dominio específica", evento.eventType)
            }
        }
    }
}
