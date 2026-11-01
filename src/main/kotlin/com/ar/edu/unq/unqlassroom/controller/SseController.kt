package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.service.SseNotificationService
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@RequestMapping("/sse")
class SseController(
    private val sseNotificationService: SseNotificationService,
) {

    @GetMapping("/eventos", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun suscribirEventos(
        authentication: Authentication,
        response: HttpServletResponse,
    ): SseEmitter {
        // Headers recomendados para evitar buffering en proxies inversos (ej. NGINX)
        response.addHeader("X-Accel-Buffering", "no")
        response.addHeader("Cache-Control", "no-cache")
        return sseNotificationService.suscribir(authentication.name)
    }
}

