package com.ar.edu.unq.unqlassroom.controller.dtos

import jakarta.validation.constraints.NotEmpty

data class AgregarAlumnosRequestDTO(
    @field:NotEmpty(message = "La lista de usernames no puede estar vacía")
    val usernames: List<String> = emptyList(),
)
