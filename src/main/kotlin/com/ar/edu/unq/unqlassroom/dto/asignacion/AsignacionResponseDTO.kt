package com.ar.edu.unq.unqlassroom.dto.asignacion

import com.ar.edu.unq.unqlassroom.model.Asignacion
import com.ar.edu.unq.unqlassroom.model.GrupoAsignacion
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
        fun desdeModelo(
            asignacion: Asignacion,
            gruposAMostrar: List<GrupoAsignacion> = asignacion.grupos
        ): AsignacionResponseDTO = AsignacionResponseDTO(
            id = asignacion.id!!,
            cursoId = asignacion.curso.id!!,
            titulo = asignacion.titulo,
            descripcion = asignacion.descripcion,
            tipo = asignacion.tipo,
            templateRepoName = asignacion.templateRepoName,
            fechaLimite = asignacion.fechaLimite,
            grupos = gruposAMostrar.map { GrupoAsignacionResponseDTO.desdeModelo(it) },
        )
    }
}
