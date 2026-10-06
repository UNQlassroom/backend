package com.ar.edu.unq.unqlassroom.dto.auth.response

import com.ar.edu.unq.unqlassroom.model.Usuario

data class GitHubLoginResponseDTO(
    val id: Long,
    val username: String,
    val esDocente: Boolean,
    val email: String?,
    val nombreCompleto: String?,
) {
    companion object {
        fun desdeModelo(usuario: Usuario): GitHubLoginResponseDTO = GitHubLoginResponseDTO(
            id = usuario.id!!,
            username = usuario.username,
            esDocente = usuario.esDocente,
            email = usuario.email,
            nombreCompleto = usuario.nombreCompleto,
        )
    }
}
