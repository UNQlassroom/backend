package com.ar.edu.unq.unqlassroom.dto.usuario.response

import com.ar.edu.unq.unqlassroom.model.Usuario

data class UsuarioResponseDTO(
    val id: Long? = null,
    val username: String,
    val esDocente: Boolean = false,
    val email: String? = null,
    val nombreCompleto: String? = null,
) {
    companion object {
        fun desdeModelo(usuario: Usuario): UsuarioResponseDTO = UsuarioResponseDTO(
            id = usuario.id,
            username = usuario.username,
            esDocente = usuario.esDocente,
            email = usuario.email,
            nombreCompleto = usuario.nombreCompleto,
        )
    }
}
