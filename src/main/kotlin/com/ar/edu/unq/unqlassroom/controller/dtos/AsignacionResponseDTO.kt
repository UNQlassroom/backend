package com.ar.edu.unq.unqlassroom.controller.dtos

import com.ar.edu.unq.unqlassroom.model.Asignacion
import com.ar.edu.unq.unqlassroom.model.TipoAsignacion
import java.time.LocalDateTime

data class AsignacionResponseDTO(
    val id: Long,
    val cursoId: Long,
    val titulo: String,
    val descripcion: String?,
    val tipo: TipoAsignacion,
    val templateRepoName: String,
    val fechaLimite: LocalDateTime?,
    val grupos: List<GrupoAsignacionResponseDTO>,
) {
    companion object {
        fun desdeModelo(asignacion: Asignacion): AsignacionResponseDTO = AsignacionResponseDTO(
            id = asignacion.id ?: 0L,
            cursoId = asignacion.curso.id ?: 0L,
            titulo = asignacion.titulo,
            descripcion = asignacion.descripcion,
            tipo = asignacion.tipo,
            templateRepoName = asignacion.templateRepoName,
            fechaLimite = asignacion.fechaLimite,
            grupos = asignacion.grupos.map { GrupoAsignacionResponseDTO.desdeModelo(it) },
        )
    }
}
