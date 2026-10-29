package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler de eventos de Webhook para 'workflow_run' y reportes de linter ('linter_report') de GitHub Actions.
 *
 * Escucha los eventos de ejecución de workflows de CI/CD:
 * - Si el estado es 'in_progress' (o encolado): actualiza el estado de CI a 'pending'.
 * - Si el estado es 'completed': evalúa la 'conclusion' para asignar 'success' o 'failure'.
 * - Si el payload contiene un reporte de análisis estático del linter, lo procesa y persiste.
 */
@Component
class WorkflowRunWebhookHandler(
    private val repositorioRepository: RepositorioRepository,
    private val objectMapper: ObjectMapper,
    private val linterCIService: LinterCIService? = null,
) : WebhookEventHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val SUPPORTED_EVENTS = setOf(
            "workflow_run",
            "linter_report",
            "linter",
            "static_analysis",
            "checkstyle_report",
            "ktlint_report",
            "detekt_report",
            "eslint_report",
            "sarif_report",
        )
    }

    override fun canHandle(event: WebhookEvent): Boolean {
        return event.eventType.lowercase() in SUPPORTED_EVENTS
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

        val nuevoEstadoCI = determinarEstadoCI(rootNode)
        val repositorioActualizado = if (nuevoEstadoCI != null) {
            repositorio.estadoCI = nuevoEstadoCI
            val guardado = repositorioRepository.save(repositorio)
            logger.info(
                "Repositorio '{}' actualizado por {}. Nuevo estado de CI: '{}'",
                repoName,
                event.eventType,
                nuevoEstadoCI
            )
            guardado
        } else {
            repositorio
        }

        try {
            linterCIService?.procesarReporteDeEvento(repositorioActualizado, rootNode)
        } catch (e: Exception) {
            logger.error(
                "Fallo secundario al procesar o persistir reporte de linter para el repositorio '{}': {}",
                repoName,
                e.message
            )
        }
    }

    private fun extraerRepoName(rootNode: JsonNode): String? {
        val name = rootNode.path("repository").path("name").asText(null)
        if (!name.isNullOrBlank()) return name
        val wfRepoName = rootNode.path("workflow_run").path("repository").path("name").asText(null)
        if (!wfRepoName.isNullOrBlank()) return wfRepoName
        val repoName = rootNode.path("repo_name").asText(null)
        if (!repoName.isNullOrBlank()) return repoName
        val repoNombre = rootNode.path("repoNombre").asText(null)
        if (!repoNombre.isNullOrBlank()) return repoNombre
        return null
    }

    private fun determinarEstadoCI(rootNode: JsonNode): String? {
        val workflowNode = rootNode.path("workflow_run")
        val status = workflowNode.path("status").asText(null)?.lowercase()
            ?: rootNode.path("action").asText(null)?.lowercase()

        val conclusion = workflowNode.path("conclusion").asText(null)?.lowercase()

        return when {
            status in listOf("in_progress", "queued", "requested", "waiting", "pending") -> "pending"
            status == "completed" -> {
                when (conclusion) {
                    "success", "neutral", "skipped" -> "success"
                    "failure", "timed_out", "cancelled", "action_required" -> "failure"
                    else -> if (!conclusion.isNullOrBlank()) conclusion else "success"
                }
            }
            conclusion != null -> {
                when (conclusion) {
                    "success", "neutral", "skipped" -> "success"
                    "failure", "timed_out", "cancelled" -> "failure"
                    else -> conclusion
                }
            }
            else -> null
        }
    }
}
