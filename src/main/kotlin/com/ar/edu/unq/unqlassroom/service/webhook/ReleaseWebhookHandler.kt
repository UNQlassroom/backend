package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.GrupoAsignacionRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler de eventos de Webhook para 'release' de GitHub.
 *
 * Procesa eventos de tipo RELEASE (típicamente con action = "released" o "published"),
 * extrayendo la URL del release publicado y el nombre del repositorio, y marcando
 * la entrega en la entidad GrupoAsignacion correspondiente.
 */
@Component
class ReleaseWebhookHandler(
    private val grupoAsignacionRepository: GrupoAsignacionRepository,
    private val objectMapper: ObjectMapper,
) : WebhookEventHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun canHandle(event: WebhookEvent): Boolean {
        if (!event.eventType.equals("release", ignoreCase = true)) {
            return false
        }
        val action = event.action ?: extraerActionDePayload(event.payload)
        return action.equals("released", ignoreCase = true)
    }

    @Transactional
    override fun handle(event: WebhookEvent) {
        val rootNode = objectMapper.readTree(event.payload)
        val repoName = extraerRepoName(rootNode)
        if (repoName.isNullOrBlank()) {
            logger.warn("No se pudo extraer el nombre del repositorio en el evento release {}", event.id)
            return
        }

        val grupo = grupoAsignacionRepository.findByRepositorioNombreIgnoreCase(repoName)
        if (grupo == null) {
            logger.info("No se encontró ningún GrupoAsignacion asociado al repositorio '{}'", repoName)
            return
        }

        val releaseUrl = extraerReleaseUrl(rootNode)

        grupo.registrarEntrega(releaseUrl)
        grupoAsignacionRepository.save(grupo)

        logger.info(
            "Entrega registrada para el grupo '{}' (repo '{}'). Entregas totales: {}, URL: '{}'",
            grupo.nombre ?: grupo.id,
            repoName,
            grupo.cantidadEntregas,
            grupo.releaseUrl
        )
    }

    private fun extraerActionDePayload(payload: String): String? {
        return try {
            val node = objectMapper.readTree(payload).get("action")
            if (node != null && !node.isNull) node.asText() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun extraerRepoName(rootNode: JsonNode): String? {
        val name = rootNode.path("repository").path("name").asText(null)
        if (!name.isNullOrBlank()) return name
        val repoName = rootNode.path("repo_name").asText(null)
        if (!repoName.isNullOrBlank()) return repoName
        return null
    }

    private fun extraerReleaseUrl(rootNode: JsonNode): String? {
        val htmlUrl = rootNode.path("release").path("html_url").asText(null)
        if (!htmlUrl.isNullOrBlank()) return htmlUrl
        val directHtml = rootNode.path("html_url").asText(null)
        if (!directHtml.isNullOrBlank()) return directHtml
        val releaseUrl = rootNode.path("release_url").asText(null)
        if (!releaseUrl.isNullOrBlank()) return releaseUrl
        return null
    }
}
