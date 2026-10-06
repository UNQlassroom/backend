package com.ar.edu.unq.unqlassroom.dto.asignacion.request

import com.ar.edu.unq.unqlassroom.model.Asignacion
import com.ar.edu.unq.unqlassroom.model.GrupoAsignacion
import com.ar.edu.unq.unqlassroom.model.Usuario
import jakarta.validation.constraints.NotBlank

data class CrearGrupoRequestDTO(
    @field:NotBlank(message = "El nombre del grupo es obligatorio")
    val nombre: String,

    val integrantesUsernames: List<String> = emptyList(),
) {
    fun aModelo(asignacion: Asignacion? = null): GrupoAsignacion = GrupoAsignacion(
        nombre = this.nombre,
        asignacion = asignacion,
        integrantes = this.integrantesUsernames.map { Usuario(username = it.trim()) }.toMutableList(),
    )
}
