package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEventStatus
import com.ar.edu.unq.unqlassroom.repository.WebhookEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class WebhookProcessorServiceTest {

    @Mock
    private lateinit var webhookEventRepository: WebhookEventRepository

    private val objectMapper = ObjectMapper()
    private lateinit var service: WebhookProcessorService

    @BeforeEach
    fun setUp() {
        service = WebhookProcessorService(webhookEventRepository, objectMapper)
    }

    private fun eqStatus(status: WebhookEventStatus): WebhookEventStatus {
        eq(status)
        return status
    }

    private fun anyPageable(): Pageable {
        any(Pageable::class.java)
        return PageRequest.of(0, 50)
    }

    @Test
    fun `procesarEventosPendientes procesa evento push y cambia estado a PROCESSED`() {
        val payload = """
            {
              "repository": { "name": "2026s1_c1_intro_tp1_juan" },
              "pusher": { "name": "juanperez" },
              "commits": [
                { "id": "commit1", "message": "feat: entrega tp" }
              ]
            }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            payload = payload,
            eventType = "push",
            status = WebhookEventStatus.PENDING,
        )

        `when`(webhookEventRepository.findByStatusForUpdate(eqStatus(WebhookEventStatus.PENDING), anyPageable()))
            .thenReturn(listOf(evento))

        service.procesarEventosPendientes()

        assertEquals(WebhookEventStatus.PROCESSED, evento.status)
        assertNotNull(evento.processedAt)
        assertNull(evento.errorMessage)
        verify(webhookEventRepository).save(evento)
    }

    @Test
    fun `procesarEventosPendientes con payload JSON invalido cambia estado a ERROR`() {
        val invalidPayload = "{ invalid json content ... "

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            payload = invalidPayload,
            eventType = "push",
            status = WebhookEventStatus.PENDING,
        )

        `when`(webhookEventRepository.findByStatusForUpdate(eqStatus(WebhookEventStatus.PENDING), anyPageable()))
            .thenReturn(listOf(evento))

        service.procesarEventosPendientes()

        assertEquals(WebhookEventStatus.ERROR, evento.status)
        assertNotNull(evento.processedAt)
        assertNotNull(evento.errorMessage)
        verify(webhookEventRepository).save(evento)
    }

    @Test
    fun `procesarEventosPendientes no hace nada cuando no hay eventos pendientes`() {
        `when`(webhookEventRepository.findByStatusForUpdate(eqStatus(WebhookEventStatus.PENDING), anyPageable()))
            .thenReturn(emptyList())

        service.procesarEventosPendientes()

        verify(webhookEventRepository, never()).save(any())
    }
}
