package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.exception.InvalidWebhookSignatureException
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEventStatus
import com.ar.edu.unq.unqlassroom.repository.WebhookEventRepository
import com.ar.edu.unq.unqlassroom.util.WebhookSignatureValidator
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
class WebhookIngestionServiceTest {

    @Mock
    private lateinit var webhookEventRepository: WebhookEventRepository

    @Mock
    private lateinit var webhookSignatureValidator: WebhookSignatureValidator

    private val objectMapper = ObjectMapper()
    private lateinit var service: WebhookIngestionService

    private fun anyWebhookEvent(): WebhookEvent {
        any(WebhookEvent::class.java)
        return WebhookEvent(payload = "", eventType = "")
    }

    @BeforeEach
    fun setUp() {
        service = WebhookIngestionService(webhookEventRepository, webhookSignatureValidator, objectMapper)
    }

    @Test
    fun `registrarEvento con firma valida persiste evento con estado PENDING, deliveryId y extrae action`() {
        val payload = "{\"action\":\"completed\",\"workflow_run\":{\"id\":123}}"
        val signature = "sha256=validsignature"
        val eventType = "workflow_run"
        val deliveryId = "72d3162e-cc78-11e3-81ab-4c9367dc0958"

        doNothing().`when`(webhookSignatureValidator).validate(payload, signature)
        `when`(webhookEventRepository.existsByDeliveryId(deliveryId)).thenReturn(false)
        `when`(webhookEventRepository.save(anyWebhookEvent())).thenAnswer { invocation ->
            invocation.getArgument<WebhookEvent>(0)
        }

        service.registrarEvento(payload, signature, eventType, deliveryId)

        verify(webhookSignatureValidator).validate(payload, signature)
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
    fun `registrarEvento con firma invalida lanza excepcion y no persiste nada`() {
        val payload = "{\"action\":\"completed\"}"
        val signature = "sha256=invalidsignature"
        val eventType = "workflow_run"
        val deliveryId = "delivery-123"

        doThrow(InvalidWebhookSignatureException()).`when`(webhookSignatureValidator).validate(payload, signature)

        assertThrows(InvalidWebhookSignatureException::class.java) {
            service.registrarEvento(payload, signature, eventType, deliveryId)
        }

        verify(webhookSignatureValidator).validate(payload, signature)
        verifyNoInteractions(webhookEventRepository)
    }

    @Test
    fun `registrarEvento con deliveryId duplicado no persiste de nuevo y sale temprano`() {
        val payload = "{\"ref\":\"refs/heads/main\"}"
        val signature = "sha256=validsignature"
        val eventType = "push"
        val deliveryId = "existing-delivery-id"

        doNothing().`when`(webhookSignatureValidator).validate(payload, signature)
        `when`(webhookEventRepository.existsByDeliveryId(deliveryId)).thenReturn(true)

        service.registrarEvento(payload, signature, eventType, deliveryId)

        verify(webhookSignatureValidator).validate(payload, signature)
        verify(webhookEventRepository).existsByDeliveryId(deliveryId)
        verifyNoMoreInteractions(webhookEventRepository)
    }
}
