package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.controller.advice.RestExceptionHandler
import com.ar.edu.unq.unqlassroom.exception.InvalidWebhookSignatureException
import com.ar.edu.unq.unqlassroom.service.WebhookIngestionService
import com.ar.edu.unq.unqlassroom.util.WebhookSignatureValidator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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
    private lateinit var webhookIngestionService: WebhookIngestionService

    @InjectMocks
    private lateinit var webhookController: WebhookController

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(webhookController)
            .setControllerAdvice(RestExceptionHandler())
            .build()
    }

    @Test
    fun `recibirWebhookGithub con firma valida y deliveryId delega a webhookIngestionService y retorna 200 OK`() {
        val payload = "{\"ref\":\"refs/heads/main\",\"repository\":{\"name\":\"repo-tp\"}}"
        val signature = "sha256=validsignature"
        val eventType = "push"
        val deliveryId = "72d3162e-cc78-11e3-81ab-4c9367dc0958"

        doNothing().`when`(webhookSignatureValidator).validate(payload, signature)

        mockMvc.perform(
            post("/api/webhooks/github")
                .header("X-Hub-Signature-256", signature)
                .header("X-GitHub-Event", eventType)
                .header("X-GitHub-Delivery", deliveryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andExpect(status().isOk)

        verify(webhookSignatureValidator).validate(payload, signature)
        verify(webhookIngestionService).registrarEvento(payload, eventType, deliveryId)
    }

    @Test
    fun `recibirWebhookGithub con firma invalida retorna 401 Unauthorized y no ingesta evento`() {
        val payload = "{\"ref\":\"refs/heads/main\"}"
        val signature = "sha256=invalidsignature"

        doThrow(InvalidWebhookSignatureException())
            .`when`(webhookSignatureValidator).validate(payload, signature)

        mockMvc.perform(
            post("/api/webhooks/github")
                .header("X-Hub-Signature-256", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
        )
            .andExpect(status().isUnauthorized)

        verifyNoInteractions(webhookIngestionService)
    }
}
