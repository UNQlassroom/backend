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

@ExtendWith(MockitoExtension::class)
class RepositoryWebhookHandlerTest {

    @Mock
    private lateinit var repositorioRepository: RepositorioRepository

    private val objectMapper = ObjectMapper()
    private lateinit var handler: RepositoryWebhookHandler

    @BeforeEach
    fun setUp() {
        handler = RepositoryWebhookHandler(repositorioRepository, objectMapper)
    }

    @Test
    fun `canHandle returns true for repository events case-insensitive`() {
        val eventUpper = WebhookEvent(eventType = "REPOSITORY", payload = "{}")
        val eventLower = WebhookEvent(eventType = "repository", payload = "{}")
        val otherEvent = WebhookEvent(eventType = "issues", payload = "{}")

        assertTrue(handler.canHandle(eventUpper))
        assertTrue(handler.canHandle(eventLower))
        assertFalse(handler.canHandle(otherEvent))
    }

    @Test
    fun `handle creates template repository on created event when is_template is true`() {
        val payload = """
            {
                "action": "created",
                "repository": {
                    "name": "template-algo1",
                    "html_url": "https://github.com/unqlassroom/template-algo1",
                    "description": "Template para algoritmos",
                    "is_template": true
                }
            }
        """.trimIndent()
        val event = WebhookEvent(eventType = "repository", action = "created", payload = payload)

        `when`(repositorioRepository.findByNombreIgnoreCase("template-algo1")).thenReturn(null)

        handler.handle(event)

        val captor = ArgumentCaptor.forClass(Repositorio::class.java)
        verify(repositorioRepository).save(captor.capture())

        val saved = captor.value
        assertEquals("template-algo1", saved.nombre)
        assertEquals("https://github.com/unqlassroom/template-algo1", saved.htmlUrl)
        assertEquals("Template para algoritmos", saved.descripcion)
        assertTrue(saved.esTemplate)
        assertFalse(saved.eliminado)
    }

    @Test
    fun `handle updates existing repository to template on edited event when is_template is true`() {
        val payload = """
            {
                "action": "edited",
                "repository": {
                    "name": "template-algo1",
                    "html_url": "https://github.com/unqlassroom/template-algo1",
                    "description": "Descripción actualizada",
                    "is_template": true
                }
            }
        """.trimIndent()
        val event = WebhookEvent(eventType = "repository", action = "edited", payload = payload)

        val existing = Repositorio(
            id = 5L,
            nombre = "template-algo1",
            htmlUrl = "https://github.com/unqlassroom/template-algo1",
            esTemplate = false,
            descripcion = "Vieja",
            eliminado = true
        )
        `when`(repositorioRepository.findByNombreIgnoreCase("template-algo1")).thenReturn(existing)

        handler.handle(event)

        assertTrue(existing.esTemplate)
        assertEquals("Descripción actualizada", existing.descripcion)
        assertFalse(existing.eliminado)
        verify(repositorioRepository).save(existing)
    }

    @Test
    fun `handle marks repository as deleted on deleted event`() {
        val payload = """
            {
                "action": "deleted",
                "repository": {
                    "name": "template-viejo",
                    "is_template": true
                }
            }
        """.trimIndent()
        val event = WebhookEvent(eventType = "repository", action = "deleted", payload = payload)

        val existing = Repositorio(
            id = 10L,
            nombre = "template-viejo",
            htmlUrl = "https://github.com/unqlassroom/template-viejo",
            esTemplate = true,
            eliminado = false
        )
        `when`(repositorioRepository.findByNombreIgnoreCase("template-viejo")).thenReturn(existing)

        handler.handle(event)

        assertTrue(existing.eliminado)
        verify(repositorioRepository).save(existing)
    }

    @Test
    fun `handle unsets esTemplate when repository is edited to not be a template`() {
        val payload = """
            {
                "action": "edited",
                "repository": {
                    "name": "template-algo1",
                    "is_template": false
                }
            }
        """.trimIndent()
        val event = WebhookEvent(eventType = "repository", action = "edited", payload = payload)

        val existing = Repositorio(
            id = 5L,
            nombre = "template-algo1",
            htmlUrl = "https://github.com/unqlassroom/template-algo1",
            esTemplate = true
        )
        `when`(repositorioRepository.findByNombreIgnoreCase("template-algo1")).thenReturn(existing)

        handler.handle(event)

        assertFalse(existing.esTemplate)
        verify(repositorioRepository).save(existing)
    }

    @Test
    fun `handle does nothing when created repo is not a template`() {
        val payload = """
            {
                "action": "created",
                "repository": {
                    "name": "repo-comun",
                    "is_template": false
                }
            }
        """.trimIndent()
        val event = WebhookEvent(eventType = "repository", action = "created", payload = payload)

        `when`(repositorioRepository.findByNombreIgnoreCase("repo-comun")).thenReturn(null)

        handler.handle(event)

        verify(repositorioRepository, never()).save(any(Repositorio::class.java))
    }

    @Test
    fun `handle returns early when repository node is missing or name is blank`() {
        val eventNoRepo = WebhookEvent(eventType = "repository", payload = "{}")
        handler.handle(eventNoRepo)
        verify(repositorioRepository, never()).save(any(Repositorio::class.java))

        val eventBlankName = WebhookEvent(eventType = "repository", payload = """{"repository": {"name": "  "}}""")
        handler.handle(eventBlankName)
        verify(repositorioRepository, never()).save(any(Repositorio::class.java))
    }
}
