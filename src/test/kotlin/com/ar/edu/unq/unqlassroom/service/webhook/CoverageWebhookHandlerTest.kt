package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.MetricaCI
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.ar.edu.unq.unqlassroom.service.CommitAnalysisService
import com.ar.edu.unq.unqlassroom.service.MetricasCIService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CoverageWebhookHandlerTest {

    @Mock
    private lateinit var repositorioRepository: RepositorioRepository

    @Mock
    private lateinit var metricasCIService: MetricasCIService

    @Mock
    private lateinit var commitAnalysisService: CommitAnalysisService

    private val objectMapper = ObjectMapper()
    private lateinit var handler: CoverageWebhookHandler

    private fun <T> anyObject(clazz: Class<T>): T {
        any(clazz)
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    @BeforeEach
    fun setUp() {
        handler = CoverageWebhookHandler(
            repositorioRepository = repositorioRepository,
            objectMapper = objectMapper,
            metricasCIService = metricasCIService,
            commitAnalysisService = commitAnalysisService,
        )
    }

    @Test
    fun `canHandle retorna true solo para evento coverage_report case-insensitive`() {
        val coverageLower = WebhookEvent(eventType = "coverage_report", payload = "{}")
        val coverageUpper = WebhookEvent(eventType = "COVERAGE_REPORT", payload = "{}")
        val linterEvent = WebhookEvent(eventType = "linter_report", payload = "{}")
        val workflowRunEvent = WebhookEvent(eventType = "workflow_run", payload = "{}")
        val pushEvent = WebhookEvent(eventType = "push", payload = "{}")

        assertTrue(handler.canHandle(coverageLower))
        assertTrue(handler.canHandle(coverageUpper))
        assertFalse(handler.canHandle(linterEvent))
        assertFalse(handler.canHandle(workflowRunEvent))
        assertFalse(handler.canHandle(pushEvent))
    }

    @Test
    fun `handle procesa metricas de cobertura con payload directo`() {
        val payload = """
        {
          "repository": {
            "name": "tp1-calculadora"
          },
          "sha": "693df8c88738f249ec059e60e3775d7f750c8675",
          "jacoco_xml": "<report name=\"test\"></report>"
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "coverage_report",
            payload = payload
        )

        val repoExistente = Repositorio(
            id = 1L,
            nombre = "tp1-calculadora",
            htmlUrl = "https://github.com/UNQlassroom-cursos/tp1-calculadora",
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp1-calculadora")).thenReturn(repoExistente)
        val metricaSimulada = MetricaCI(
            commitSha = "693df8c88738f249ec059e60e3775d7f750c8675",
            coberturaLineas = 85.0,
            lineasCubiertas = 17,
            lineasPerdidas = 3,
            testsEjecutados = 5,
            testsPasados = 5,
            testsFallados = 0,
            testsSalteados = 0,
            cumpleUmbralCobertura = true,
            repositorio = repoExistente,
        )
        `when`(metricasCIService.procesarMetricasDeEvento(eq(repoExistente) ?: repoExistente, anyObject(JsonNode::class.java)))
            .thenReturn(metricaSimulada)

        handler.handle(evento)

        verify(metricasCIService).procesarMetricasDeEvento(
            eq(repoExistente) ?: repoExistente,
            anyObject(JsonNode::class.java)
        )
        verify(commitAnalysisService).analizarCommitsDePush(
            eq(repoExistente) ?: repoExistente,
            anyObject(JsonNode::class.java)
        )
    }

    @Test
    fun `handle desenvuelve payload con envoltorio timestamp de smee`() {
        val payload = """
        {
          "1793291380154": {
            "repository": {
              "name": "tp1-calculadora"
            },
            "sha": "693df8c88738f249ec059e60e3775d7f750c8675",
            "jacoco_xml": "<report name=\"test\"></report>"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "coverage_report",
            payload = payload
        )

        val repoExistente = Repositorio(
            id = 1L,
            nombre = "tp1-calculadora",
            htmlUrl = "https://github.com/UNQlassroom-cursos/tp1-calculadora",
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp1-calculadora")).thenReturn(repoExistente)

        handler.handle(evento)

        verify(repositorioRepository).findByNombreIgnoreCase("tp1-calculadora")
        verify(metricasCIService).procesarMetricasDeEvento(
            eq(repoExistente) ?: repoExistente,
            anyObject(JsonNode::class.java)
        )
        verify(commitAnalysisService).analizarCommitsDePush(
            eq(repoExistente) ?: repoExistente,
            anyObject(JsonNode::class.java)
        )
    }

    @Test
    fun `handle no realiza acciones cuando el repositorio no existe en BD`() {
        val payload = """
        {
          "repository": {
            "name": "repo-inexistente"
          },
          "jacoco_xml": "<report></report>"
        }
        """.trimIndent()

        val evento = WebhookEvent(eventType = "coverage_report", payload = payload)
        `when`(repositorioRepository.findByNombreIgnoreCase("repo-inexistente")).thenReturn(null)

        handler.handle(evento)

        verifyNoInteractions(metricasCIService)
        verifyNoInteractions(commitAnalysisService)
    }
}
