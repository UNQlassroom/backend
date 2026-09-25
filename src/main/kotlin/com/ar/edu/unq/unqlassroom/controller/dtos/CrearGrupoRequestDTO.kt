package com.ar.edu.unq.unqlassroom.controller.dtos

import jakarta.validation.constraints.NotBlank

data class CrearGrupoRequestDTO(
    @field:NotBlank(message = "El nombre del grupo es obligatorio")
    val nombre: String,

    val integrantesUsernames: List<String> = emptyList(),
)
