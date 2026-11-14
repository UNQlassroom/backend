package com.ar.edu.unq.unqlassroom.dto.auth.request

import jakarta.validation.constraints.NotBlank

data class GitHubLoginRequestDTO(
    @field:NotBlank(message = "El código de GitHub es requerido")
    val code: String,
    val esDocente: Boolean? = false,
)
