package com.ar.edu.unq.unqlassroom.dto.usuario.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class ActualizarDatosRequestDTO(
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

    @field:NotBlank(message = "El correo electrónico no puede estar vacío")
    @field:Pattern(
        regexp = "^[A-Za-z0-9._%+-]+@([A-Za-z0-9-]+\\.)+(com|com\\.ar)$",
        message = "El correo electrónico debe ser válido, contener '@' y finalizar con '.com' o '.com.ar'",
    )
    val email: String = "",

    @field:NotBlank(message = "El DNI no puede estar vacío")
    @field:Pattern(
        regexp = "^\\d{7,8}$",
        message = "El DNI debe contener entre 7 y 8 dígitos numéricos",
    )
    val dni: String = "",
)
