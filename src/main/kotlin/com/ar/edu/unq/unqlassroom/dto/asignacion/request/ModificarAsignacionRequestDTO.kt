package com.ar.edu.unq.unqlassroom.dto.asignacion.request

import java.time.LocalDateTime

data class ModificarAsignacionRequestDTO(
    val descripcion: String? = null,
    val fechaLimite: LocalDateTime? = null,
)
