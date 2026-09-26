package com.ar.edu.unq.unqlassroom.controller.dtos

import com.ar.edu.unq.unqlassroom.model.Usuario
import jakarta.validation.constraints.NotBlank

data class GitHubLoginRequestDTO(
    @field:NotBlank(message = "El código de GitHub es requerido")
    val code: String,
    val esDocente: Boolean? = false
)

data class GitHubLoginResponseDTO(
    val id: Long,
    val username: String,
    val esDocente: Boolean,
    val email: String?,
    val nombreCompleto: String?
) {
    companion object {
        fun desdeModelo(usuario: Usuario): GitHubLoginResponseDTO = GitHubLoginResponseDTO(
            id = usuario.id!!,
            username = usuario.username,
            esDocente = usuario.esDocente,
            email = usuario.email,
            nombreCompleto = usuario.nombreCompleto
        )
    }
}

data class AuthResponseDTO(
    val token: String? = null,
    val user: GitHubLoginResponseDTO? = null,
    val requiereUnirseAOrg: Boolean = false,
    val redirectUrl: String? = null
)

