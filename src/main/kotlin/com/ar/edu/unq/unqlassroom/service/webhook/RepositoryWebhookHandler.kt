package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler de eventos de Webhook para 'repository' de GitHub.
 *
 * Mantiene sincronizados los repositorios y su condicion de template en la base de datos local
 * cuando se crean, modifican o eliminan desde GitHub.
 */
@Component
class RepositoryWebhookHandler(
    private val repositorioRepository: RepositorioRepository,
    private val objectMapper: ObjectMapper,
) : WebhookEventHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun canHandle(event: WebhookEvent): Boolean {
        return event.eventType.equals("repository", ignoreCase = true)
    }

    @Transactional
    override fun handle(event: WebhookEvent) {
        val rootNode = objectMapper.readTree(event.payload)
        val action = (event.action ?: rootNode.path("action").asText(null))?.lowercase()

        val repoNode = rootNode.path("repository")
        if (repoNode.isMissingNode || repoNode.isNull) {
            logger.warn("No se encontró el nodo 'repository' en el evento repository {}", event.id)
            return
        }

        val repoName = extraerRepoName(repoNode)
        if (repoName.isNullOrBlank()) {
            logger.warn("No se pudo extraer el nombre del repositorio en el evento repository {}", event.id)
            return
        }

        val isTemplate = repoNode.path("is_template").asBoolean(false)
        val htmlUrl = repoNode.path("html_url").asText("")
        val description = repoNode.path("description").asText(null)

        if (action == "deleted") {
            val repoExistente = repositorioRepository.findByNombreIgnoreCase(repoName)
            if (repoExistente != null) {
                repoExistente.marcarComoEliminado()
                repositorioRepository.save(repoExistente)
                logger.info("Repositorio '{}' marcado como eliminado por evento repository (action 'deleted')", repoName)
            }
            return
        }

        if (isTemplate) {
            val repositorio = repositorioRepository.findByNombreIgnoreCase(repoName)
                ?: Repositorio(
                    nombre = repoName,
                    htmlUrl = htmlUrl,
                    esTemplate = true,
                    descripcion = description,
                )

            repositorio.htmlUrl = htmlUrl
            repositorio.marcarComoTemplate(description)

            repositorioRepository.save(repositorio)
            logger.info("Repositorio template '{}' sincronizado exitosamente por webhook (action '{}')", repoName, action)
        } else {
            val repoExistente = repositorioRepository.findByNombreIgnoreCase(repoName)
            if (repoExistente != null && repoExistente.esTemplate) {
                repoExistente.esTemplate = false
                repositorioRepository.save(repoExistente)
                logger.info("Repositorio '{}' ya no es template en GitHub; esTemplate marcado en false", repoName)
            }
        }
    }

    private fun extraerRepoName(repoNode: JsonNode): String? {
        val name = repoNode.path("name").asText(null)
        if (!name.isNullOrBlank()) return name
        val repoName = repoNode.path("repo_name").asText(null)
        if (!repoName.isNullOrBlank()) return repoName
        return null
    }
}
