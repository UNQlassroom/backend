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
    val entregada: Boolean = false,
    val fechaEntrega: LocalDateTime? = null,
    val releaseUrl: String? = null,
) {
    companion object {
        fun desdeModelo(asignacion: Asignacion): AsignacionResponseDTO {
            val gruposDTO = asignacion.grupos.map { GrupoAsignacionResponseDTO.desdeModelo(it) }
            val primerGrupo = gruposDTO.firstOrNull()
            return AsignacionResponseDTO(
                id = asignacion.id ?: 0L,
                cursoId = asignacion.curso.id ?: 0L,
                titulo = asignacion.titulo,
                descripcion = asignacion.descripcion,
                tipo = asignacion.tipo,
                templateRepoName = asignacion.templateRepoName,
                fechaLimite = asignacion.fechaLimite,
                grupos = gruposDTO,
                entregada = primerGrupo?.entregada ?: false,
                fechaEntrega = primerGrupo?.fechaEntrega,
                releaseUrl = primerGrupo?.releaseUrl,
            )
        }
    }
}
