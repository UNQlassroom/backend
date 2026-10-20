package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.Issue
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.IssueRepository
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler de eventos de Webhook para 'issues' e 'issue_comment' de GitHub.
 *
 * Mantiene sincronizados los issues y la cantidad de comentarios en la base de datos local
 * para dar soporte en tiempo real a las correcciones de los docentes.
 */
@Component
class IssueWebhookHandler(
    private val repositorioRepository: RepositorioRepository,
    private val issueRepository: IssueRepository,
    private val objectMapper: ObjectMapper,
) : WebhookEventHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun canHandle(event: WebhookEvent): Boolean {
        return event.eventType.equals("issues", ignoreCase = true) ||
               event.eventType.equals("issue_comment", ignoreCase = true)
    }

    @Transactional
    override fun handle(event: WebhookEvent) {
        val rootNode = objectMapper.readTree(event.payload)
        val repoName = extraerRepoName(rootNode)
        if (repoName.isNullOrBlank()) {
            logger.warn("No se pudo extraer el nombre del repositorio en el evento {} {}", event.eventType, event.id)
            return
        }

        val repositorio = repositorioRepository.findByNombreIgnoreCase(repoName)
        if (repositorio == null) {
            logger.info("El repositorio '{}' no existe en la base de datos de UNQlassroom", repoName)
            return
        }

        val issueNode = rootNode.path("issue")
        if (issueNode.isMissingNode || issueNode.isNull) {
            logger.warn("No se encontró el nodo 'issue' en el payload del evento {} {}", event.eventType, event.id)
            return
        }

        // Si es una Pull Request (GitHub las envía como issues con 'pull_request' poblado), la ignoramos
        if (issueNode.hasNonNull("pull_request")) {
            return
        }

        val action = event.action ?: rootNode.path("action").asText(null)
        val numero = issueNode.path("number").asInt(0)
        if (numero <= 0) {
            logger.warn("Número de issue inválido en el evento {} {}", event.eventType, event.id)
            return
        }

        val esEventoIssues = event.eventType.equals("issues", ignoreCase = true)
        if (esEventoIssues && action.equals("deleted", ignoreCase = true)) {
            val issueExistente = issueRepository.findByRepositorioIdAndNumero(repositorio.id!!, numero)
            if (issueExistente != null) {
                issueExistente.marcarComoEliminado()
                issueRepository.save(issueExistente)
                logger.info("Issue #{} del repositorio '{}' marcado como eliminado", numero, repoName)
            }
            return
        }

        val titulo = issueNode.path("title").asText("")
        val htmlUrl = issueNode.path("html_url").asText("")
        val autor = issueNode.path("user").path("login").asText("")
        val estadoGitHub = issueNode.path("state").asText("open")
        val cantComentarios = issueNode.path("comments").asInt(0)
        val fechaCreacion = issueNode.path("created_at").asText("")
        val fechaActualizacion = issueNode.path("updated_at").asText(fechaCreacion)
        val fechaCierre = issueNode.path("closed_at").asText(null)

        val issue = issueRepository.findByRepositorioIdAndNumero(repositorio.id!!, numero)
            ?: Issue(
                numero = numero,
                titulo = titulo,
                htmlUrl = htmlUrl,
                autor = autor,
                estadoGitHub = estadoGitHub,
                cantComentarios = cantComentarios,
                fechaCreacion = fechaCreacion,
                fechaActualizacion = fechaActualizacion,
                fechaCierre = fechaCierre,
                eliminado = false,
                repositorio = repositorio,
            )

        issue.actualizarDesdeWebhook(
            titulo = titulo,
            estadoGitHub = estadoGitHub,
            cantComentarios = cantComentarios,
            fechaActualizacion = fechaActualizacion,
            fechaCierre = fechaCierre,
        )

        issueRepository.save(issue)
        logger.info(
            "Issue #{} ('{}') del repositorio '{}' actualizado por evento '{}' (action '{}'). Comentarios: {}, Estado: '{}'",
            numero,
            titulo,
            repoName,
            event.eventType,
            action,
            cantComentarios,
            estadoGitHub
        )
    }

    private fun extraerRepoName(rootNode: JsonNode): String? {
        val name = rootNode.path("repository").path("name").asText(null)
        if (!name.isNullOrBlank()) return name
        val repoName = rootNode.path("repo_name").asText(null)
        if (!repoName.isNullOrBlank()) return repoName
        return null
    }
}
