package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.ReporteLinter
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.ar.edu.unq.unqlassroom.service.LinterCIService
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
class LinterWebhookHandlerTest {

    @Mock
    private lateinit var repositorioRepository: RepositorioRepository

    @Mock
    private lateinit var linterCIService: LinterCIService

    private val objectMapper = ObjectMapper()
    private lateinit var handler: LinterWebhookHandler

    private fun <T> anyObject(clazz: Class<T>): T {
        any(clazz)
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    @BeforeEach
    fun setUp() {
        handler = LinterWebhookHandler(
            repositorioRepository = repositorioRepository,
            objectMapper = objectMapper,
            linterCIService = linterCIService,
        )
    }

    @Test
    fun `canHandle retorna true solo para evento linter_report case-insensitive`() {
        val linterLower = WebhookEvent(eventType = "linter_report", payload = "{}")
        val linterUpper = WebhookEvent(eventType = "LINTER_REPORT", payload = "{}")
        val coverageEvent = WebhookEvent(eventType = "coverage_report", payload = "{}")
        val workflowRunEvent = WebhookEvent(eventType = "workflow_run", payload = "{}")
        val pushEvent = WebhookEvent(eventType = "push", payload = "{}")

        assertTrue(handler.canHandle(linterLower))
        assertTrue(handler.canHandle(linterUpper))
        assertFalse(handler.canHandle(coverageEvent))
        assertFalse(handler.canHandle(workflowRunEvent))
        assertFalse(handler.canHandle(pushEvent))
    }

    @Test
    fun `handle procesa reporte de linter con payload directo`() {
        val payload = """
        {
          "repository": {
            "name": "tp1-calculadora"
          },
          "sha": "693df8c88738f249ec059e60e3775d7f750c8675",
          "herramienta": "checkstyle",
          "checkstyle_xml": "<checkstyle><file name=\"Calculadora.java\"></file></checkstyle>"
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "linter_report",
            payload = payload
        )

        val repoExistente = Repositorio(
            id = 1L,
            nombre = "tp1-calculadora",
            htmlUrl = "https://github.com/UNQlassroom-cursos/tp1-calculadora",
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp1-calculadora")).thenReturn(repoExistente)
        val reporteSimulado = ReporteLinter(
            commitSha = "693df8c88738f249ec059e60e3775d7f750c8675",
            herramienta = "checkstyle",
            cantidadErrores = 0,
            cantidadAdvertencias = 2,
            totalIncidencias = 2,
            repositorio = repoExistente,
        )
        `when`(linterCIService.procesarReporteDeEvento(eq(repoExistente) ?: repoExistente, anyObject(JsonNode::class.java)))
            .thenReturn(reporteSimulado)

        handler.handle(evento)

        verify(linterCIService).procesarReporteDeEvento(
            eq(repoExistente) ?: repoExistente,
            anyObject(JsonNode::class.java)
        )
    }

    @Test
    fun `handle desenvuelve payload con envoltorio timestamp de smee`() {
        val payload = """
        {
          "1793291372745": {
            "repository": {
              "name": "tp1-calculadora"
            },
            "sha": "693df8c88738f249ec059e60e3775d7f750c8675",
            "herramienta": "checkstyle",
            "checkstyle_xml": "<checkstyle></checkstyle>"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "linter_report",
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
        verify(linterCIService).procesarReporteDeEvento(
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
          "herramienta": "checkstyle"
        }
        """.trimIndent()

        val evento = WebhookEvent(eventType = "linter_report", payload = payload)
        `when`(repositorioRepository.findByNombreIgnoreCase("repo-inexistente")).thenReturn(null)

        handler.handle(evento)

        verifyNoInteractions(linterCIService)
    }
}
