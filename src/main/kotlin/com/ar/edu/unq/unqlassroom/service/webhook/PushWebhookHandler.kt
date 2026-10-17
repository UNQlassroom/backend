package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler de eventos de Webhook para 'push' de GitHub.
 *
 * Se encarga de procesar los eventos de tipo PUSH, extraer del payload la información
 * del commit (mensaje y fecha) y el estado de CI para actualizar la entidad Repositorio.
 */
@Component
class PushWebhookHandler(
    private val repositorioRepository: RepositorioRepository,
    private val objectMapper: ObjectMapper,
) : WebhookEventHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun canHandle(event: WebhookEvent): Boolean {
        return event.eventType.equals("push", ignoreCase = true)
    }

    @Transactional
    override fun handle(event: WebhookEvent) {
        val rootNode = objectMapper.readTree(event.payload)
        val repoName = extraerRepoName(rootNode)
        if (repoName.isNullOrBlank()) {
            logger.warn("No se pudo extraer el nombre del repositorio en el evento push {}", event.id)
            return
        }

        val repositorio = repositorioRepository.findByNombreIgnoreCase(repoName)
        if (repositorio == null) {
            logger.info("El repositorio '{}' no existe en la base de datos de UNQlassroom", repoName)
            return
        }

        val commitMessage = extraerCommitMessage(rootNode)
        val commitDate = extraerCommitDate(rootNode)
        val estadoCI = extraerEstadoCI(rootNode)

        repositorio.actualizarInfo(
            ultimoCommit = commitMessage ?: repositorio.ultimoCommit,
            fechaUltimoCommit = commitDate ?: repositorio.fechaUltimoCommit,
            estadoCI = estadoCI ?: repositorio.estadoCI
        )

        repositorioRepository.save(repositorio)
        logger.info(
            "Repositorio '{}' actualizado por push. Ultimo commit: '{}', fecha: '{}', CI: '{}'",
            repoName,
            repositorio.ultimoCommit,
            repositorio.fechaUltimoCommit,
            repositorio.estadoCI
        )
    }

    private fun extraerRepoName(rootNode: JsonNode): String? {
        val name = rootNode.path("repository").path("name").asText(null)
        if (!name.isNullOrBlank()) return name
        val repoName = rootNode.path("repo_name").asText(null)
        if (!repoName.isNullOrBlank()) return repoName
        return null
    }

    private fun extraerCommitMessage(rootNode: JsonNode): String? {
        val headMsg = rootNode.path("head_commit").path("message").asText(null)
        if (!headMsg.isNullOrBlank()) return headMsg

        val commits = rootNode.path("commits")
        if (commits.isArray && commits.size() > 0) {
            val lastMsg = commits.get(commits.size() - 1).path("message").asText(null)
            if (!lastMsg.isNullOrBlank()) return lastMsg
        }

        val directMsg = rootNode.path("message").asText(null)
        if (!directMsg.isNullOrBlank()) return directMsg

        return null
    }

    private fun extraerCommitDate(rootNode: JsonNode): String? {
        val headTs = rootNode.path("head_commit").path("timestamp").asText(null)
        if (!headTs.isNullOrBlank()) return headTs

        val commits = rootNode.path("commits")
        if (commits.isArray && commits.size() > 0) {
            val lastTs = commits.get(commits.size() - 1).path("timestamp").asText(null)
            if (!lastTs.isNullOrBlank()) return lastTs
        }

        val directTs = rootNode.path("timestamp").asText(null)
        if (!directTs.isNullOrBlank()) return directTs

        val directDate = rootNode.path("fecha").asText(null)
        if (!directDate.isNullOrBlank()) return directDate

        return null
    }

    private fun extraerEstadoCI(rootNode: JsonNode): String? {
        val directFields = listOf("estadoCI", "estado_ci", "ci_status", "status")
        for (field in directFields) {
            val value = rootNode.path(field).asText(null)
            if (!value.isNullOrBlank()) return value
        }

        val headCommitStatus = rootNode.path("head_commit").path("status").asText(null)
        if (!headCommitStatus.isNullOrBlank()) return headCommitStatus

        val workflowConclusion = rootNode.path("workflow_run").path("conclusion").asText(null)
        if (!workflowConclusion.isNullOrBlank()) return workflowConclusion

        val checkConclusion = rootNode.path("check_run").path("conclusion").asText(null)
        if (!checkConclusion.isNullOrBlank()) return checkConclusion

        return null
    }
}
