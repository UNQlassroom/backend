package com.ar.edu.unq.unqlassroom.dto.usuario.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class ActualizarNombreRequestDTO(
    @field:NotBlank(message = "El nombre no puede estar vacío")
    @field:Pattern(
        regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ' -]+$",
        message = "El nombre contiene caracteres inválidos",
    )
    val nombre: String = "",

    @field:NotBlank(message = "El apellido no puede estar vacío")
    @field:Pattern(
        regexp = "^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ' -]+$",
        message = "El apellido contiene caracteres inválidos",
    )
    val apellido: String = "",
)
