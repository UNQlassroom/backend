package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.GrupoAsignacion
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.GrupoAsignacionRepository
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
class ReleaseWebhookHandlerTest {

    @Mock
    private lateinit var grupoAsignacionRepository: GrupoAsignacionRepository

    private val objectMapper = ObjectMapper()
    private lateinit var handler: ReleaseWebhookHandler

    @BeforeEach
    fun setUp() {
        handler = ReleaseWebhookHandler(grupoAsignacionRepository, objectMapper)
    }

    @Test
    fun `canHandle retorna true para eventos release con accion released o published o sin accion`() {
        val eventReleased = WebhookEvent(eventType = "release", action = "released", payload = "{}")
        val eventPublished = WebhookEvent(eventType = "RELEASE", action = "published", payload = "{}")
        val eventNoAction = WebhookEvent(eventType = "release", payload = "{}")
        val eventFromPayload = WebhookEvent(eventType = "release", payload = """{"action": "released"}""")
        val eventDeleted = WebhookEvent(eventType = "release", action = "deleted", payload = "{}")
        val eventPush = WebhookEvent(eventType = "push", action = "released", payload = "{}")

        assertTrue(handler.canHandle(eventReleased))
        assertTrue(handler.canHandle(eventPublished))
        assertTrue(handler.canHandle(eventNoAction))
        assertTrue(handler.canHandle(eventFromPayload))
        assertFalse(handler.canHandle(eventDeleted))
        assertFalse(handler.canHandle(eventPush))
    }

    @Test
    fun `handle registra entrega en GrupoAsignacion extrayendo url y repo name`() {
        val payload = """
        {
          "action": "released",
          "repository": {
            "name": "tp-final-grupo-alpha"
          },
          "release": {
            "html_url": "https://github.com/UNQlassroom/tp-final-grupo-alpha/releases/tag/v1.0"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "release",
            action = "released",
            payload = payload
        )

        val repo = Repositorio(
            id = 1L,
            nombre = "tp-final-grupo-alpha",
            htmlUrl = "https://github.com/UNQlassroom/tp-final-grupo-alpha"
        )
        val grupo = GrupoAsignacion(
            id = 10L,
            nombre = "Grupo Alpha",
            repositorio = repo,
            entregada = false,
            cantidadEntregas = 0
        )

        `when`(grupoAsignacionRepository.findByRepositorioNombreIgnoreCase("tp-final-grupo-alpha"))
            .thenReturn(grupo)

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(GrupoAsignacion::class.java)
        verify(grupoAsignacionRepository).save(captor.capture())

        val grupoGuardado = captor.value
        assertTrue(grupoGuardado.entregada)
        assertEquals(1, grupoGuardado.cantidadEntregas)
        assertEquals(
            "https://github.com/UNQlassroom/tp-final-grupo-alpha/releases/tag/v1.0",
            grupoGuardado.releaseUrl
        )
        assertNotNull(grupoGuardado.fechaEntregada)
    }

    @Test
    fun `handle incrementa cantidadEntregas y actualiza url cuando ya tenia entregas previas`() {
        val payload = """
        {
          "action": "released",
          "repository": {
            "name": "tp-final-grupo-alpha"
          },
          "release": {
            "html_url": "https://github.com/UNQlassroom/tp-final-grupo-alpha/releases/tag/v2.0"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "release",
            action = "released",
            payload = payload
        )

        val grupo = GrupoAsignacion(
            id = 10L,
            nombre = "Grupo Alpha",
            entregada = true,
            cantidadEntregas = 1,
            releaseUrl = "https://github.com/UNQlassroom/tp-final-grupo-alpha/releases/tag/v1.0"
        )

        `when`(grupoAsignacionRepository.findByRepositorioNombreIgnoreCase("tp-final-grupo-alpha"))
            .thenReturn(grupo)

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(GrupoAsignacion::class.java)
        verify(grupoAsignacionRepository).save(captor.capture())

        val grupoGuardado = captor.value
        assertTrue(grupoGuardado.entregada)
        assertEquals(2, grupoGuardado.cantidadEntregas)
        assertEquals(
            "https://github.com/UNQlassroom/tp-final-grupo-alpha/releases/tag/v2.0",
            grupoGuardado.releaseUrl
        )
    }

    @Test
    fun `handle no lanza error y no guarda cuando el grupo no se encuentra en BD`() {
        val payload = """
        {
          "action": "released",
          "repository": {
            "name": "repo-sin-grupo"
          },
          "release": {
            "html_url": "https://github.com/UNQlassroom/repo-sin-grupo/releases/tag/v1.0"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(eventType = "release", payload = payload)
        `when`(grupoAsignacionRepository.findByRepositorioNombreIgnoreCase("repo-sin-grupo"))
            .thenReturn(null)

        handler.handle(evento)

        verify(grupoAsignacionRepository, never()).save(any())
    }

    @Test
    fun `handle no realiza acciones cuando no se encuentra nombre de repo en payload`() {
        val payload = """
        {
          "release": {
            "html_url": "https://github.com/UNQlassroom/releases/tag/v1.0"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(eventType = "release", payload = payload)

        handler.handle(evento)

        verifyNoInteractions(grupoAsignacionRepository)
    }
}
