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
    private lateinit var linterCIService: LinterCIService

    private val objectMapper = ObjectMapper()
    private lateinit var handler: WorkflowRunWebhookHandler

    @BeforeEach
    fun setUp() {
        handler = WorkflowRunWebhookHandler(repositorioRepository, objectMapper, linterCIService)
    }

    private fun anyJsonNode(): JsonNode {
        any(JsonNode::class.java)
        return objectMapper.createObjectNode()
    }

    private fun eqRepo(repo: Repositorio): Repositorio {
        eq(repo)
        return repo
    }

    @Test
    fun `canHandle retorna true para eventos workflow_run y reportes de linter case-insensitive`() {
        val eventLower = WebhookEvent(eventType = "workflow_run", payload = "{}")
        val eventUpper = WebhookEvent(eventType = "WORKFLOW_RUN", payload = "{}")
        val eventLinter = WebhookEvent(eventType = "linter_report", payload = "{}")
        val eventStatic = WebhookEvent(eventType = "STATIC_ANALYSIS", payload = "{}")
        val eventPush = WebhookEvent(eventType = "push", payload = "{}")
        val eventRelease = WebhookEvent(eventType = "release", payload = "{}")

        assertTrue(handler.canHandle(eventLower))
        assertTrue(handler.canHandle(eventUpper))
        assertTrue(handler.canHandle(eventLinter))
        assertTrue(handler.canHandle(eventStatic))
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
        `when`(repositorioRepository.save(repoExistente)).thenReturn(repoExistente)

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
        `when`(repositorioRepository.save(repoExistente)).thenReturn(repoExistente)

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
        `when`(repositorioRepository.save(repoExistente)).thenReturn(repoExistente)

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(Repositorio::class.java)
        verify(repositorioRepository).save(captor.capture())

        assertEquals("failure", captor.value.estadoCI)
    }

    @Test
    fun `handle procesa reporte de linter enviado directamente por el CI mediante evento linter_report`() {
        val payload = """
        {
          "repo_name": "tp1-intro-juan",
          "commit_sha": "sha-1234",
          "linter": {
            "incidencias": [
              {
                "archivo": "src/App.kt",
                "linea": 10,
                "severidad": "WARNING",
                "regla": "unused import"
              }
            ]
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
            nombre = "tp1-intro-juan",
            htmlUrl = "https://github.com/UNQlassroom-cursos/tp1-intro-juan",
            estadoCI = "success"
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp1-intro-juan")).thenReturn(repoExistente)

        handler.handle(evento)

        verify(repositorioRepository, never()).save(any())
        verify(linterCIService).procesarReporteDeEvento(eqRepo(repoExistente), anyJsonNode())
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
        verifyNoInteractions(linterCIService)
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
        verifyNoInteractions(linterCIService)
    }
}
