package com.ar.edu.unq.unqlassroom.dto.asignacion.response

import com.ar.edu.unq.unqlassroom.dto.curso.response.RepositorioDTO
import com.ar.edu.unq.unqlassroom.dto.usuario.response.UsuarioResponseDTO
import com.ar.edu.unq.unqlassroom.model.GrupoAsignacion
import java.time.LocalDateTime

data class GrupoAsignacionResponseDTO(
    val id: Long,
    val nombre: String?,
    val integrantes: List<UsuarioResponseDTO>,
    val repositorio: RepositorioDTO?,
    val entregada: Boolean = false,
    val fechaEntregada: LocalDateTime? = null,
    val releaseUrl: String? = null,
    val calificacion: Int? = null,
    val observaciones: String? = null,
    val fechaCalificacion: LocalDateTime? = null,
) {
    companion object {
        fun desdeModelo(grupo: GrupoAsignacion): GrupoAsignacionResponseDTO = GrupoAsignacionResponseDTO(
            id = grupo.id!!,
            nombre = grupo.nombre,
            integrantes = grupo.integrantes.map { UsuarioResponseDTO.desdeModelo(it) },
            repositorio = grupo.repositorio?.let { RepositorioDTO.desdeModelo(it) },
            entregada = grupo.entregada,
            fechaEntregada = grupo.fechaEntregada,
            releaseUrl = grupo.releaseUrl,
            calificacion = grupo.calificacion,
            observaciones = grupo.observaciones,
            fechaCalificacion = grupo.fechaCalificacion,
        )
    }
}
