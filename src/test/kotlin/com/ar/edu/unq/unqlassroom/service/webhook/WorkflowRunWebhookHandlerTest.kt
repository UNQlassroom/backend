package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class WorkflowRunWebhookHandlerTest {

    @Mock
    private lateinit var repositorioRepository: RepositorioRepository

    @Mock
    private lateinit var metricasCIService: MetricasCIService

    private val objectMapper = ObjectMapper()
    private lateinit var handler: WorkflowRunWebhookHandler

    private fun <T> anyObject(clazz: Class<T>): T {
        any(clazz)
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    @BeforeEach
    fun setUp() {
        handler = WorkflowRunWebhookHandler(repositorioRepository, objectMapper, metricasCIService)
    }

    @Test
    fun `canHandle retorna true para eventos workflow_run y ci_metrics case-insensitive`() {
        val eventLower = WebhookEvent(eventType = "workflow_run", payload = "{}")
        val eventUpper = WebhookEvent(eventType = "WORKFLOW_RUN", payload = "{}")
        val eventMetrics = WebhookEvent(eventType = "ci_metrics", payload = "{}")
        val eventCoverage = WebhookEvent(eventType = "coverage_report", payload = "{}")
        val eventPush = WebhookEvent(eventType = "push", payload = "{}")
        val eventRelease = WebhookEvent(eventType = "release", payload = "{}")

        assertTrue(handler.canHandle(eventLower))
        assertTrue(handler.canHandle(eventUpper))
        assertTrue(handler.canHandle(eventMetrics))
        assertTrue(handler.canHandle(eventCoverage))
        assertFalse(handler.canHandle(eventPush))
        assertFalse(handler.canHandle(eventRelease))
    }

    @Test
    fun `handle asigna pending cuando el workflow esta in_progress`() {
        val payload = """
        {
          "action": "in_progress",
          "repository": {
            "name": "tp1-intro-juan"
          },
          "workflow_run": {
            "status": "in_progress",
            "conclusion": null
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "workflow_run",
            payload = payload
        )

        val repoExistente = Repositorio(
            id = 1L,
            nombre = "tp1-intro-juan",
            htmlUrl = "https://github.com/UNQlassroom-cursos/tp1-intro-juan",
            estadoCI = "sin_ci"
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp1-intro-juan")).thenReturn(repoExistente)
        `when`(repositorioRepository.save(any(Repositorio::class.java))).thenAnswer { it.arguments[0] }

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(Repositorio::class.java)
        verify(repositorioRepository).save(captor.capture())

        assertEquals("pending", captor.value.estadoCI)
    }

    @Test
    fun `handle asigna success cuando el workflow esta completed con conclusion success`() {
        val payload = """
        {
          "action": "completed",
          "repository": {
            "name": "tp1-intro-juan"
          },
          "workflow_run": {
            "status": "completed",
            "conclusion": "success"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "workflow_run",
            payload = payload
        )

        val repoExistente = Repositorio(
            id = 1L,
            nombre = "tp1-intro-juan",
            htmlUrl = "https://github.com/UNQlassroom-cursos/tp1-intro-juan",
            estadoCI = "pending"
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp1-intro-juan")).thenReturn(repoExistente)
        `when`(repositorioRepository.save(any(Repositorio::class.java))).thenAnswer { it.arguments[0] }

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(Repositorio::class.java)
        verify(repositorioRepository).save(captor.capture())

        assertEquals("success", captor.value.estadoCI)
    }

    @Test
    fun `handle asigna failure cuando el workflow esta completed con conclusion failure`() {
        val payload = """
        {
          "action": "completed",
          "repository": {
            "name": "tp1-intro-juan"
          },
          "workflow_run": {
            "status": "completed",
            "conclusion": "failure"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "workflow_run",
            payload = payload
        )

        val repoExistente = Repositorio(
            id = 1L,
            nombre = "tp1-intro-juan",
            htmlUrl = "https://github.com/UNQlassroom-cursos/tp1-intro-juan",
            estadoCI = "pending"
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp1-intro-juan")).thenReturn(repoExistente)
        `when`(repositorioRepository.save(any(Repositorio::class.java))).thenAnswer { it.arguments[0] }

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(Repositorio::class.java)
        verify(repositorioRepository).save(captor.capture())

        assertEquals("failure", captor.value.estadoCI)
    }

    @Test
    fun `handle invoca metricasCIService cuando recibe un evento ci_metrics`() {
        val payload = """
        {
          "repo_name": "tp1-intro-juan",
          "commit_sha": "sha123",
          "coberturaLineas": 88.5,
          "testsEjecutados": 10,
          "testsPasados": 10,
          "testsFallados": 0,
          "testsSalteados": 0
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "ci_metrics",
            payload = payload
        )

        val repoExistente = Repositorio(
            id = 1L,
            nombre = "tp1-intro-juan",
            htmlUrl = "https://github.com/UNQlassroom-cursos/tp1-intro-juan",
            estadoCI = "success"
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp1-intro-juan")).thenReturn(repoExistente)

        handler.handle(evento)

        verify(metricasCIService).procesarMetricasDeEvento(
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
          "workflow_run": {
            "status": "completed",
            "conclusion": "success"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(eventType = "workflow_run", payload = payload)
        `when`(repositorioRepository.findByNombreIgnoreCase("repo-inexistente")).thenReturn(null)

        handler.handle(evento)

        verify(repositorioRepository, never()).save(any())
        verifyNoInteractions(metricasCIService)
    }

    @Test
    fun `handle no realiza acciones cuando no se encuentra nombre de repo`() {
        val payload = """
        {
          "workflow_run": {
            "status": "completed",
            "conclusion": "success"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(eventType = "workflow_run", payload = payload)

        handler.handle(evento)

        verifyNoInteractions(repositorioRepository)
        verifyNoInteractions(metricasCIService)
    }
}
