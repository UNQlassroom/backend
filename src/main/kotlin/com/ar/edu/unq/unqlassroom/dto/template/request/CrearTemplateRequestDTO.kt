package com.ar.edu.unq.unqlassroom.dto.template.request

import jakarta.validation.constraints.NotBlank

data class CrearTemplateRequestDTO(
    @field:NotBlank(message = "El nombre del template es obligatorio")
    val name: String,

    val description: String? = null,
)
