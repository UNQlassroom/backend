package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.ar.edu.unq.unqlassroom.service.LinterCIService
import com.ar.edu.unq.unqlassroom.service.MetricasCIService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler de eventos de Webhook para 'workflow_run', métricas de CI ('ci_metrics') y reportes de linter ('linter_report') de GitHub Actions.
 *
 * Escucha los eventos de ejecución de workflows de CI/CD:
 * - Si el estado es 'in_progress' (o encolado): actualiza el estado de CI a 'pending'.
 * - Si el estado es 'completed': evalúa la 'conclusion' para asignar 'success' o 'failure'.
 * - Cuando resulta en 'failure', consulta la API de GitHub Actions para extraer el step
 *   fallido y el extracto acotado del log del error, y los persiste en el repositorio.
 * - Si el payload contiene métricas de cobertura (JaCoCo) y/o tests (JUnit), las procesa y persiste.
 * - Si el payload contiene un reporte de análisis estático del linter, lo procesa y persiste.
 */
@Component
class WorkflowRunWebhookHandler(
    private val repositorioRepository: RepositorioRepository,
    private val objectMapper: ObjectMapper,
    private val gitHubRepoService: GitHubRepoService? = null,
    private val metricasCIService: MetricasCIService? = null,
    private val linterCIService: LinterCIService? = null,
) : WebhookEventHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val SUPPORTED_EVENTS = setOf(
            "workflow_run",
            "ci_metrics",
            "coverage_report",
            "test_report",
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

            when (nuevoEstadoCI) {
                "failure" -> procesarDiagnosticoFalla(rootNode, repoName, repositorio)
                "success" -> repositorio.limpiarFallaCI()
            }

            val guardado = repositorioRepository.save(repositorio)
            logger.info(
                "Repositorio '{}' actualizado por {}. Nuevo estado de CI: '{}'",
                repoName,
                event.eventType,
                nuevoEstadoCI
            )
            guardado ?: repositorio
        } else {
            repositorio
        }

        try {
            metricasCIService?.procesarMetricasDeEvento(repositorioActualizado, rootNode)
        } catch (e: Exception) {
            logger.error(
                "Fallo secundario al procesar o persistir métricas de CI para el repositorio '{}': {}",
                repoName,
                e.message
            )
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

    private fun procesarDiagnosticoFalla(
        rootNode: JsonNode,
        repoName: String,
        repositorio: com.ar.edu.unq.unqlassroom.model.Repositorio,
    ) {
        val workflowNode = rootNode.path("workflow_run")
        val runId = workflowNode.path("id").asLong(0L).takeIf { it > 0L }
        val runHtmlUrl = workflowNode.path("html_url").asText(null)?.takeIf { it.isNotBlank() }
        val fallbackEtapa = workflowNode.path("name").asText(null)?.takeIf { it.isNotBlank() }
            ?: "Ejecución de workflow"
        val fallbackUrl = runHtmlUrl ?: "${repositorio.htmlUrl}/actions"

        if (runId != null && gitHubRepoService != null) {
            try {
                val diagnostico = gitHubRepoService.obtenerDiagnosticoCorridaFallida(
                    repoName = repoName,
                    runId = runId,
                    runHtmlUrl = runHtmlUrl,
                )
                repositorio.registrarFallaCI(
                    etapaFallida = diagnostico.etapaFallida,
                    logError = diagnostico.logError,
                    runUrl = diagnostico.runUrl,
                    runId = diagnostico.runId,
                )
                return
            } catch (e: Exception) {
                logger.warn(
                    "No se pudo consultar el diagnóstico detallado de GitHub Actions para repo '{}' (runId {}): {}",
                    repoName,
                    runId,
                    e.message
                )
            }
        }

        repositorio.registrarFallaCI(
            etapaFallida = fallbackEtapa,
            logError = "La corrida de CI/CD finalizó con errores. Revisá el detalle completo en GitHub Actions.",
            runUrl = fallbackUrl,
            runId = runId,
        )
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
