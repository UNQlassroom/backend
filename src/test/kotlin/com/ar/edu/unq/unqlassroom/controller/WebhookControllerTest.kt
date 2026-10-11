package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEventStatus
import com.ar.edu.unq.unqlassroom.repository.WebhookEventRepository
import com.ar.edu.unq.unqlassroom.util.WebhookSignatureValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class WebhookControllerTest {

    @Mock
    private lateinit var webhookSignatureValidator: WebhookSignatureValidator

    @Mock
    private lateinit var webhookEventRepository: WebhookEventRepository

    @InjectMocks
    private lateinit var webhookController: WebhookController

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(webhookController).build()
    }

    @Test
    fun `recibirWebhookGithub con firma valida guarda evento PENDING y retorna 200 OK inmediatamente`() {
        val payload = "{\"ref\":\"refs/heads/main\",\"repository\":{\"name\":\"repo-tp\"}}"
        val signature = "sha256=validsignature"
        val eventType = "push"

        `when`(webhookSignatureValidator.isValid(payload, signature)).thenReturn(true)

        mockMvc.perform(
            post("/api/webhooks/github")
                .header("X-Hub-Signature-256", signature)
                .header("X-GitHub-Event", eventType)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andExpect(status().isOk)

        val captor = ArgumentCaptor.forClass(WebhookEvent::class.java)
        verify(webhookEventRepository).save(captor.capture())

        val eventoGuardado = captor.value
        assertEquals(payload, eventoGuardado.payload)
        assertEquals(eventType, eventoGuardado.eventType)
        assertEquals(WebhookEventStatus.PENDING, eventoGuardado.status)
        assertNotNull(eventoGuardado.createdAt)
    }

    @Test
    fun `recibirWebhookGithub con firma invalida retorna 401 Unauthorized y no persiste en base de datos`() {
        val payload = "{\"ref\":\"refs/heads/main\"}"
        val signature = "sha256=invalidsignature"

        `when`(webhookSignatureValidator.isValid(payload, signature)).thenReturn(false)

        mockMvc.perform(
            post("/api/webhooks/github")
                .header("X-Hub-Signature-256", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andExpect(status().isUnauthorized)

        verify(webhookEventRepository, never()).save(any())
    }
}
