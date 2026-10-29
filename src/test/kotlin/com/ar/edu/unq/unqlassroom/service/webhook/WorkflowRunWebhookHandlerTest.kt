package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.integration.github.service.DiagnosticoCIInfo
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
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
    private lateinit var gitHubRepoService: GitHubRepoService

    @Mock
    private lateinit var metricasCIService: MetricasCIService

    @Mock
    private lateinit var linterCIService: LinterCIService

    private val objectMapper = ObjectMapper()
    private lateinit var handler: WorkflowRunWebhookHandler

    private fun <T> anyObject(clazz: Class<T>): T {
        any(clazz)
        @Suppress("UNCHECKED_CAST")
        return null as T
    }

    @BeforeEach
    fun setUp() {
        handler = WorkflowRunWebhookHandler(
            repositorioRepository = repositorioRepository,
            objectMapper = objectMapper,
            gitHubRepoService = gitHubRepoService,
            metricasCIService = metricasCIService,
            linterCIService = linterCIService,
        )
    }

    @Test
    fun `canHandle retorna true para eventos workflow_run ci_metrics y linter_report case-insensitive`() {
        val eventLower = WebhookEvent(eventType = "workflow_run", payload = "{}")
        val eventUpper = WebhookEvent(eventType = "WORKFLOW_RUN", payload = "{}")
        val eventMetrics = WebhookEvent(eventType = "ci_metrics", payload = "{}")
        val eventCoverage = WebhookEvent(eventType = "coverage_report", payload = "{}")
        val eventLinter = WebhookEvent(eventType = "linter_report", payload = "{}")
        val eventStatic = WebhookEvent(eventType = "STATIC_ANALYSIS", payload = "{}")
        val eventPush = WebhookEvent(eventType = "push", payload = "{}")
        val eventRelease = WebhookEvent(eventType = "release", payload = "{}")

        assertTrue(handler.canHandle(eventLower))
        assertTrue(handler.canHandle(eventUpper))
        assertTrue(handler.canHandle(eventMetrics))
        assertTrue(handler.canHandle(eventCoverage))
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
        verifyNoInteractions(metricasCIService)
        verifyNoInteractions(linterCIService)
    }

    @Test
    fun `handle con failure y runId consulta GitHubRepoService y persiste diagnostico detallado`() {
        val payload = """
        {
          "action": "completed",
          "repository": {
            "name": "tp1-intro-juan"
          },
          "workflow_run": {
            "id": 999111,
            "name": "Java CI with Maven",
            "status": "completed",
            "conclusion": "failure",
            "html_url": "https://github.com/UNQlassroom-cursos/tp1-intro-juan/actions/runs/999111"
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

        val diagnosticoEsperado = DiagnosticoCIInfo(
            runId = 999111L,
            jobName = "build",
            etapaFallida = "mvn compile",
            logError = "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin: Compilation failure",
            runUrl = "https://github.com/UNQlassroom-cursos/tp1-intro-juan/actions/runs/999111"
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp1-intro-juan")).thenReturn(repoExistente)
        `when`(
            gitHubRepoService.obtenerDiagnosticoCorridaFallida(
                repoName = "tp1-intro-juan",
                runId = 999111L,
                runHtmlUrl = "https://github.com/UNQlassroom-cursos/tp1-intro-juan/actions/runs/999111"
            )
        ).thenReturn(diagnosticoEsperado)

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(Repositorio::class.java)
        verify(repositorioRepository).save(captor.capture())

        val guardado = captor.value
        assertEquals("failure", guardado.estadoCI)
        assertEquals(999111L, guardado.ciRunId)
        assertEquals("mvn compile", guardado.ciEtapaFallida)
        assertEquals(
            "[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin: Compilation failure",
            guardado.ciLogError
        )
        assertEquals("https://github.com/UNQlassroom-cursos/tp1-intro-juan/actions/runs/999111", guardado.ciRunUrl)
    }

    @Test
    fun `handle con failure persiste fallback si GitHubRepoService lanza excepcion`() {
        val payload = """
        {
          "action": "completed",
          "repository": {
            "name": "tp1-intro-juan"
          },
          "workflow_run": {
            "id": 999222,
            "name": "Build & Test",
            "status": "completed",
            "conclusion": "failure",
            "html_url": "https://github.com/UNQlassroom-cursos/tp1-intro-juan/actions/runs/999222"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(eventType = "workflow_run", payload = payload)
        val repoExistente = Repositorio(
            id = 1L,
            nombre = "tp1-intro-juan",
            htmlUrl = "https://github.com/UNQlassroom-cursos/tp1-intro-juan",
            estadoCI = "pending"
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp1-intro-juan")).thenReturn(repoExistente)
        `when`(
            gitHubRepoService.obtenerDiagnosticoCorridaFallida(
                repoName = "tp1-intro-juan",
                runId = 999222L,
                runHtmlUrl = "https://github.com/UNQlassroom-cursos/tp1-intro-juan/actions/runs/999222"
            )
        ).thenThrow(RuntimeException("API down"))

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(Repositorio::class.java)
        verify(repositorioRepository).save(captor.capture())

        val guardado = captor.value
        assertEquals("failure", guardado.estadoCI)
        assertEquals(999222L, guardado.ciRunId)
        assertEquals("Build & Test", guardado.ciEtapaFallida)
        assertEquals("https://github.com/UNQlassroom-cursos/tp1-intro-juan/actions/runs/999222", guardado.ciRunUrl)
        assertNotNull(guardado.ciLogError)
    }

    @Test
    fun `handle con success limpia datos de falla de CI previa`() {
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

        val evento = WebhookEvent(eventType = "workflow_run", payload = payload)
        val repoExistente = Repositorio(
            id = 1L,
            nombre = "tp1-intro-juan",
            htmlUrl = "https://github.com/UNQlassroom-cursos/tp1-intro-juan",
            estadoCI = "failure",
            ciRunId = 999111L,
            ciEtapaFallida = "mvn test",
            ciLogError = "Error log",
            ciRunUrl = "https://github.com/UNQlassroom-cursos/tp1-intro-juan/actions/runs/999111"
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp1-intro-juan")).thenReturn(repoExistente)

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(Repositorio::class.java)
        verify(repositorioRepository).save(captor.capture())

        val guardado = captor.value
        assertEquals("success", guardado.estadoCI)
        assertNull(guardado.ciRunId)
        assertNull(guardado.ciEtapaFallida)
        assertNull(guardado.ciLogError)
        assertNull(guardado.ciRunUrl)
    }
}
