package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.ar.edu.unq.unqlassroom.service.CommitAnalysisService
import com.ar.edu.unq.unqlassroom.service.MetricasCIService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler de eventos de Webhook dedicado a reportes de cobertura y tests ('coverage_report')
 * enviados directamente por los workflows de CI.
 */
@Component
class CoverageWebhookHandler(
    private val repositorioRepository: RepositorioRepository,
    private val objectMapper: ObjectMapper,
    private val metricasCIService: MetricasCIService,
    private val commitAnalysisService: CommitAnalysisService? = null,
) : WebhookEventHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun canHandle(event: WebhookEvent): Boolean {
        return event.eventType.equals("coverage_report", ignoreCase = true)
    }

    @Transactional
    override fun handle(event: WebhookEvent) {
        val parsedNode = objectMapper.readTree(event.payload)
        val rootNode = desenpaquetarSiEsNecesario(parsedNode)

        val repoName = extraerRepoName(rootNode)
        if (repoName.isNullOrBlank()) {
            logger.warn("No se pudo extraer el nombre del repositorio en el evento de cobertura {} {}", event.eventType, event.id)
            return
        }

        val repositorio = repositorioRepository.findByNombreIgnoreCase(repoName)
        if (repositorio == null) {
            logger.info("El repositorio '{}' no existe en la base de datos de UNQlassroom", repoName)
            return
        }

        try {
            val metrica = metricasCIService.procesarMetricasDeEvento(repositorio, rootNode)
            if (metrica != null) {
                logger.info(
                    "Métricas de cobertura y tests procesadas exitosamente para '{}': {}% cobertura, {} tests ejecutados",
                    repoName,
                    metrica.coberturaLineas,
                    metrica.testsEjecutados
                )
            } else {
                logger.warn("No se generaron métricas de CI para repositorio '{}' a partir del evento {}", repoName, event.eventType)
            }
        } catch (e: Exception) {
            logger.error(
                "Fallo al procesar o persistir métricas de CI para el repositorio '{}': {}",
                repoName,
                e.message,
                e
            )
            throw e
        }

        try {
            commitAnalysisService?.analizarCommitsDePush(repositorio, rootNode)
        } catch (e: Exception) {
            logger.error(
                "Fallo secundario al analizar commits desde evento coverage_report para '{}': {}",
                repoName,
                e.message
            )
        }
    }

    private fun desenpaquetarSiEsNecesario(node: JsonNode): JsonNode {
        if (node.isObject && node.size() == 1) {
            val key = node.fieldNames().next()
            val child = node.get(key)
            if (child != null && child.isObject && (key.matches(Regex("^\\d+$")) || key == "body" || key == "payload")) {
                return child
            }
        }
        return node
    }

    private fun extraerRepoName(rootNode: JsonNode): String? {
        val name = rootNode.path("repository").path("name").asText(null)
        if (!name.isNullOrBlank()) return name
        val repoName = rootNode.path("repo_name").asText(null)
        if (!repoName.isNullOrBlank()) return repoName
        val repoNombre = rootNode.path("repoNombre").asText(null)
        if (!repoNombre.isNullOrBlank()) return repoNombre
        val repositoryName = rootNode.path("repository_name").asText(null)
        if (!repositoryName.isNullOrBlank()) return repositoryName

        val wfRepoName = rootNode.path("workflow_run").path("repository").path("name").asText(null)
        if (!wfRepoName.isNullOrBlank()) return wfRepoName

        val repoString = rootNode.path("repository").asText(null)
        if (!repoString.isNullOrBlank() && !rootNode.path("repository").isObject) {
            return repoString.substringAfterLast("/")
        }

        return null
    }
}
