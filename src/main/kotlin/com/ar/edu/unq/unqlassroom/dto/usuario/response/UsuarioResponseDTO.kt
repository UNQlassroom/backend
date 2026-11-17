package com.ar.edu.unq.unqlassroom.dto.usuario.response

import com.ar.edu.unq.unqlassroom.model.Usuario

data class UsuarioResponseDTO(
    val id: Long? = null,
    val username: String,
    val esDocente: Boolean = false,
    val email: String? = null,
    val dni: String? = null,
    val nombreCompleto: String? = null,
    val activo: Boolean = true,
) {
    companion object {
        fun desdeModelo(usuario: Usuario, activo: Boolean = true): UsuarioResponseDTO = UsuarioResponseDTO(
            id = usuario.id,
            username = usuario.username,
            esDocente = usuario.esDocente,
            email = usuario.email,
            dni = usuario.dni,
            nombreCompleto = usuario.nombreCompleto,
            activo = activo,
        )
    }
}
