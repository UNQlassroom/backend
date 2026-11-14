package com.ar.edu.unq.unqlassroom.dto.asignacion.request

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class CalificarAsignacionRequestDTO(
    @field:NotNull(message = "El grupoId es obligatorio")
    val grupoId: Long,

    @field:NotNull(message = "La nota es obligatoria")
    @field:Min(value = 1, message = "La nota debe ser entre 1 y 10")
    @field:Max(value = 10, message = "La nota debe ser entre 1 y 10")
    val calificacion: Int,

    val observaciones: String? = null,
)
