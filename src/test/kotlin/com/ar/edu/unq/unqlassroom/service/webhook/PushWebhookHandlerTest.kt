package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
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
class PushWebhookHandlerTest {

    @Mock
    private lateinit var repositorioRepository: RepositorioRepository

    private val objectMapper = ObjectMapper()
    private lateinit var handler: PushWebhookHandler

    @BeforeEach
    fun setUp() {
        handler = PushWebhookHandler(repositorioRepository, objectMapper)
    }

    @Test
    fun `canHandle retorna true solo para eventos de tipo push case-insensitive`() {
        val eventPushLower = WebhookEvent(eventType = "push", payload = "{}")
        val eventPushUpper = WebhookEvent(eventType = "PUSH", payload = "{}")
        val eventPushMixed = WebhookEvent(eventType = "Push", payload = "{}")
        val eventRelease = WebhookEvent(eventType = "release", payload = "{}")
        val eventOrg = WebhookEvent(eventType = "organization", payload = "{}")

        assertTrue(handler.canHandle(eventPushLower))
        assertTrue(handler.canHandle(eventPushUpper))
        assertTrue(handler.canHandle(eventPushMixed))
        assertFalse(handler.canHandle(eventRelease))
        assertFalse(handler.canHandle(eventOrg))
    }

    @Test
    fun `handle extrae mensaje, fecha y estadoCI desde head_commit y actualiza Repositorio`() {
        val payload = """
        {
          "repository": {
            "name": "tp1-intro-juan"
          },
          "head_commit": {
            "id": "commit123456",
            "message": "feat: resolucion ejercicio 1",
            "timestamp": "2026-10-16T15:30:00Z"
          },
          "estadoCI": "success"
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "push",
            payload = payload
        )

        val repoExistente = Repositorio(
            id = 1L,
            nombre = "tp1-intro-juan",
            htmlUrl = "https://github.com/UNQlassroom/tp1-intro-juan",
            ultimoCommit = "commit viejo",
            fechaUltimoCommit = "2026-10-10T10:00:00Z",
            estadoCI = "pending"
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp1-intro-juan")).thenReturn(repoExistente)

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(Repositorio::class.java)
        verify(repositorioRepository).save(captor.capture())

        val repoGuardado = captor.value
        assertEquals("feat: resolucion ejercicio 1", repoGuardado.ultimoCommit)
        assertEquals("2026-10-16T15:30:00Z", repoGuardado.fechaUltimoCommit)
        assertEquals("success", repoGuardado.estadoCI)
    }

    @Test
    fun `handle extrae mensaje y fecha desde array commits cuando no viene head_commit`() {
        val payload = """
        {
          "repository": {
            "name": "tp2-obj-equipo1"
          },
          "commits": [
            {
              "message": "commit previo",
              "timestamp": "2026-10-15T10:00:00Z"
            },
            {
              "message": "fix: correcion de tests",
              "timestamp": "2026-10-16T18:00:00Z"
            }
          ],
          "estado_ci": "failure"
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "push",
            payload = payload
        )

        val repoExistente = Repositorio(
            id = 2L,
            nombre = "tp2-obj-equipo1",
            htmlUrl = "https://github.com/UNQlassroom/tp2-obj-equipo1"
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp2-obj-equipo1")).thenReturn(repoExistente)

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(Repositorio::class.java)
        verify(repositorioRepository).save(captor.capture())

        val repoGuardado = captor.value
        assertEquals("fix: correcion de tests", repoGuardado.ultimoCommit)
        assertEquals("2026-10-16T18:00:00Z", repoGuardado.fechaUltimoCommit)
        assertEquals("failure", repoGuardado.estadoCI)
    }

    @Test
    fun `handle no lanza error y no guarda cuando el repositorio no existe en BD`() {
        val payload = """
        {
          "repository": {
            "name": "repo-inexistente"
          },
          "head_commit": {
            "message": "test",
            "timestamp": "2026-10-16T12:00:00Z"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(eventType = "push", payload = payload)
        `when`(repositorioRepository.findByNombreIgnoreCase("repo-inexistente")).thenReturn(null)

        handler.handle(evento)

        verify(repositorioRepository, never()).save(any())
    }

    @Test
    fun `handle no realiza acciones cuando el payload no contiene nombre de repositorio`() {
        val payload = """
        {
          "head_commit": {
            "message": "test sin repo"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(eventType = "push", payload = payload)

        handler.handle(evento)

        verifyNoInteractions(repositorioRepository)
    }
}
