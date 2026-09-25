package com.ar.edu.unq.unqlassroom.controller.dtos

import com.ar.edu.unq.unqlassroom.model.GrupoAsignacion
import java.time.LocalDateTime

data class GrupoAsignacionResponseDTO(
    val id: Long,
    val nombre: String?,
    val integrantes: List<String>,
    val repositorio: RepositorioDTO?,
    val entregada: Boolean = false,
    val fechaEntrega: LocalDateTime? = null,
    val releaseUrl: String? = null,
) {
    companion object {
        fun desdeModelo(grupo: GrupoAsignacion): GrupoAsignacionResponseDTO = GrupoAsignacionResponseDTO(
            id = grupo.id ?: 0L,
            nombre = grupo.nombre,
            integrantes = grupo.integrantes.map { it.username },
            repositorio = RepositorioDTO.desdeModelo(grupo.repositorio),
            entregada = grupo.entregada,
            fechaEntrega = grupo.fechaEntrega,
            releaseUrl = grupo.releaseUrl,
        )
    }
}
