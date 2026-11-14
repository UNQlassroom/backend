package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.service.SseNotificationService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExtendWith(MockitoExtension::class)
class SseControllerTest {

    @Mock
    private lateinit var sseNotificationService: SseNotificationService

    @Mock
    private lateinit var authentication: Authentication

    @InjectMocks
    private lateinit var sseController: SseController

    @Test
    fun `suscribirEventos retorna el SseEmitter y configura headers anti buffering`() {
        val emitterEsperado = SseEmitter(0L)
        `when`(authentication.name).thenReturn("docente1")
        `when`(sseNotificationService.suscribir("docente1")).thenReturn(emitterEsperado)

        val response = MockHttpServletResponse()
        val resultado = sseController.suscribirEventos(authentication, response)

        assertNotNull(resultado)
        assertEquals(emitterEsperado, resultado)
        assertEquals("no", response.getHeader("X-Accel-Buffering"))
        assertEquals("no-cache", response.getHeader("Cache-Control"))
        verify(sseNotificationService).suscribir("docente1")
    }
}

