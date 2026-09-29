package com.ar.edu.unq.unqlassroom.controller.dtos

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull

data class CalificarAsignacionRequestDTO(
    val grupoId: Long? = null,
    val alumnoUsername: String? = null,

    @field:NotNull(message = "La nota es obligatoria")
    @field:Min(value = 1, message = "La nota debe ser entre 1 y 10")
    @field:Max(value = 10, message = "La nota debe ser entre 1 y 10")
    val calificacion: Int? = null,

    val observaciones: String? = null,

)
