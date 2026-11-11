package com.ar.edu.unq.unqlassroom.dto.usuario.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class ActualizarNombreRequestDTO(
    @field:NotBlank(message = "El nombre y apellido no puede estar vacío")
    @field:Size(max = 35, message = "El nombre y apellido no puede exceder los 35 caracteres")
    @field:Pattern(
        regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ' -]+$",
        message = "El nombre y apellido contiene caracteres inválidos",
    )
    val nombreCompleto: String = "",
)
