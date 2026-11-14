package com.ar.edu.unq.unqlassroom.dto.curso.response

import com.ar.edu.unq.unqlassroom.model.Inscripcion

data class AlumnoResponseDTO(
    val username: String,
    val role: String,
    val state: String,
    val nombreCompleto: String? = null,
) {
    companion object {
        fun desdeModelo(inscripcion: Inscripcion): AlumnoResponseDTO = AlumnoResponseDTO(
            username = inscripcion.usuario.username,
            role = inscripcion.githubRole,
            state = inscripcion.usuario.githubState,
            nombreCompleto = inscripcion.usuario.nombreCompleto,
        )
    }
}
