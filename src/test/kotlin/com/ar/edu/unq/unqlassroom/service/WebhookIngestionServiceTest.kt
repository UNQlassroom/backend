package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEventStatus
import com.ar.edu.unq.unqlassroom.repository.WebhookEventRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class WebhookIngestionServiceTest {

    @Mock
    private lateinit var webhookEventRepository: WebhookEventRepository

    private val objectMapper = ObjectMapper()
    private lateinit var service: WebhookIngestionService

    private fun anyWebhookEvent(): WebhookEvent {
        any(WebhookEvent::class.java)
        return WebhookEvent(payload = "", eventType = "")
    }

    @BeforeEach
    fun setUp() {
        service = WebhookIngestionService(webhookEventRepository, objectMapper)
    }

    @Test
    fun `registrarEvento persiste evento con estado PENDING, deliveryId y extrae action`() {
        val payload = "{\"action\":\"completed\",\"workflow_run\":{\"id\":123}}"
        val eventType = "workflow_run"
        val deliveryId = "72d3162e-cc78-11e3-81ab-4c9367dc0958"

        `when`(webhookEventRepository.existsByDeliveryId(deliveryId)).thenReturn(false)
        `when`(webhookEventRepository.save(anyWebhookEvent())).thenAnswer { invocation ->
            invocation.getArgument<WebhookEvent>(0)
        }

        service.registrarEvento(payload, eventType, deliveryId)

        val captor = ArgumentCaptor.forClass(WebhookEvent::class.java)
        verify(webhookEventRepository).save(captor.capture())

        val guardado = captor.value
        assertEquals(payload, guardado.payload)
        assertEquals(eventType, guardado.eventType)
        assertEquals("completed", guardado.action)
        assertEquals(deliveryId, guardado.deliveryId)
        assertEquals(WebhookEventStatus.PENDING, guardado.status)
        assertNotNull(guardado.createdAt)
    }

    @Test
    fun `registrarEvento con deliveryId duplicado no persiste de nuevo y sale temprano`() {
        val payload = "{\"ref\":\"refs/heads/main\"}"
        val eventType = "push"
        val deliveryId = "existing-delivery-id"

        `when`(webhookEventRepository.existsByDeliveryId(deliveryId)).thenReturn(true)

        service.registrarEvento(payload, eventType, deliveryId)

        verify(webhookEventRepository).existsByDeliveryId(deliveryId)
        verifyNoMoreInteractions(webhookEventRepository)
    }
}
