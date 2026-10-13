package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEventStatus
import com.ar.edu.unq.unqlassroom.repository.WebhookEventRepository
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

    private lateinit var service: WebhookIngestionService

    private fun anyWebhookEvent(): WebhookEvent {
        any(WebhookEvent::class.java)
        return WebhookEvent(payload = "", eventType = "")
    }

    @BeforeEach
    fun setUp() {
        service = WebhookIngestionService(webhookEventRepository)
    }

    @Test
    fun `registrarEvento persiste evento con estado PENDING y datos correctos`() {
        val payload = "{\"ref\":\"refs/heads/main\"}"
        val eventType = "push"

        `when`(webhookEventRepository.save(anyWebhookEvent())).thenAnswer { invocation ->
            invocation.getArgument<WebhookEvent>(0)
        }

        val resultado = service.registrarEvento(payload, eventType)

        val captor = ArgumentCaptor.forClass(WebhookEvent::class.java)
        verify(webhookEventRepository).save(captor.capture())

        val guardado = captor.value
        assertEquals(payload, guardado.payload)
        assertEquals(eventType, guardado.eventType)
        assertEquals(WebhookEventStatus.PENDING, guardado.status)
        assertNotNull(guardado.createdAt)

        assertEquals(payload, resultado.payload)
        assertEquals(eventType, resultado.eventType)
        assertEquals(WebhookEventStatus.PENDING, resultado.status)
    }
}
