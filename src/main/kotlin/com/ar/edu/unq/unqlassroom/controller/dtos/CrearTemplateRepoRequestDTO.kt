package com.ar.edu.unq.unqlassroom.controller.dtos

import jakarta.validation.constraints.NotBlank

data class CrearTemplateRepoRequestDTO(
    @field:NotBlank(message = "El nombre del template es obligatorio")
    val name: String,

    val description: String? = null,
)
