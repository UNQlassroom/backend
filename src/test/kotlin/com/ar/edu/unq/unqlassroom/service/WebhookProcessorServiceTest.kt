package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEventStatus
import com.ar.edu.unq.unqlassroom.repository.WebhookEventRepository
import com.ar.edu.unq.unqlassroom.service.webhook.WebhookEventHandler
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

    @Mock
    private lateinit var webhookEventHandler: WebhookEventHandler

    private lateinit var service: WebhookProcessorService

    @BeforeEach
    fun setUp() {
        service = WebhookProcessorService(
            webhookEventRepository = webhookEventRepository,
            handlers = listOf(webhookEventHandler)
        )
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
    fun `procesarEventosPendientes delega al handler correspondiente y cambia estado a PROCESSED`() {
        val payload = """{"action": "added"}"""
        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            payload = payload,
            eventType = "membership",
            status = WebhookEventStatus.PENDING,
        )

        `when`(webhookEventRepository.findByStatusForUpdate(eqStatus(WebhookEventStatus.PENDING), anyPageable()))
            .thenReturn(listOf(evento))
        `when`(webhookEventHandler.canHandle(evento)).thenReturn(true)

        service.procesarEventosPendientes()

        verify(webhookEventHandler).handle(evento)
        assertEquals(WebhookEventStatus.PROCESSED, evento.status)
        assertNotNull(evento.processedAt)
        assertNull(evento.errorMessage)
        verify(webhookEventRepository).save(evento)
    }

    @Test
    fun `procesarEventosPendientes cambia estado a ERROR si el handler lanza excepcion`() {
        val payload = """{"invalid": "data"}"""
        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            payload = payload,
            eventType = "push",
            status = WebhookEventStatus.PENDING,
        )

        `when`(webhookEventRepository.findByStatusForUpdate(eqStatus(WebhookEventStatus.PENDING), anyPageable()))
            .thenReturn(listOf(evento))
        `when`(webhookEventHandler.canHandle(evento)).thenReturn(true)
        doThrow(RuntimeException("Error procesando push en handler")).`when`(webhookEventHandler).handle(evento)

        service.procesarEventosPendientes()

        verify(webhookEventHandler).handle(evento)
        assertEquals(WebhookEventStatus.ERROR, evento.status)
        assertNotNull(evento.processedAt)
        assertEquals("Error procesando push en handler", evento.errorMessage)
        verify(webhookEventRepository).save(evento)
    }

    @Test
    fun `procesarEventosPendientes marca PROCESSED si ningun handler soporta el evento`() {
        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            payload = "{}",
            eventType = "star",
            status = WebhookEventStatus.PENDING,
        )

        `when`(webhookEventRepository.findByStatusForUpdate(eqStatus(WebhookEventStatus.PENDING), anyPageable()))
            .thenReturn(listOf(evento))
        `when`(webhookEventHandler.canHandle(evento)).thenReturn(false)

        service.procesarEventosPendientes()

        verify(webhookEventHandler, never()).handle(evento)
        assertEquals(WebhookEventStatus.PROCESSED, evento.status)
        assertNotNull(evento.processedAt)
        assertNull(evento.errorMessage)
        verify(webhookEventRepository).save(evento)
    }

    @Test
    fun `procesarEventosPendientes no hace nada cuando no hay eventos pendientes`() {
        `when`(webhookEventRepository.findByStatusForUpdate(eqStatus(WebhookEventStatus.PENDING), anyPageable()))
            .thenReturn(emptyList())

        service.procesarEventosPendientes()

        verifyNoInteractions(webhookEventHandler)
        verify(webhookEventRepository, never()).save(any())
    }
}
