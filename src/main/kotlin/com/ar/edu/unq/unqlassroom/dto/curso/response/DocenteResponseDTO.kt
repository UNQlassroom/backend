package com.ar.edu.unq.unqlassroom.dto.curso.response

import com.ar.edu.unq.unqlassroom.model.Usuario

data class DocenteResponseDTO(
    val id: Long?,
    val username: String,
    val role: String? = "member",
    val state: String? = "pending",
    val nombreCompleto: String? = null,
    val email: String? = null,
    val dni: String? = null,
) {
    companion object {
        fun desdeModelo(usuario: Usuario): DocenteResponseDTO = DocenteResponseDTO(
            id = usuario.id,
            username = usuario.username,
            role = "member",
            state = usuario.githubState,
            nombreCompleto = usuario.nombreCompleto,
            email = usuario.email,
            dni = usuario.dni,
        )
    }
}
