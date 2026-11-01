package com.ar.edu.unq.unqlassroom.dto.sse

import java.time.LocalDateTime

data class SseEventDTO(
    val type: SseEventType,
    val cursoId: Long? = null,
    val asignacionId: Long? = null,
    val grupoId: Long? = null,
    val repoNombre: String? = null,
    val timestamp: String = LocalDateTime.now().toString(),
)

