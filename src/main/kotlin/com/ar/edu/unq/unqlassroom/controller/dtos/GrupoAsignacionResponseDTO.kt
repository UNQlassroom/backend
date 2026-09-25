package com.ar.edu.unq.unqlassroom.controller.dtos

import com.ar.edu.unq.unqlassroom.model.GrupoAsignacion

data class GrupoAsignacionResponseDTO(
    val id: Long,
    val nombre: String?,
    val integrantes: List<String>,
    val repositorio: RepositorioDTO?,
) {
    companion object {
        fun desdeModelo(grupo: GrupoAsignacion): GrupoAsignacionResponseDTO = GrupoAsignacionResponseDTO(
            id = grupo.id ?: 0L,
            nombre = grupo.nombre,
            integrantes = grupo.integrantes.map { it.username },
            repositorio = RepositorioDTO.desdeModelo(grupo.repositorio),
        )
    }
}
