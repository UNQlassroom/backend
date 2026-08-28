package com.ar.edu.unq.unqlassroom.controller.dtos

import com.ar.edu.unq.unqlassroom.model.Curso

data class CursoResponseDTO(
    val id: Long,
    val materia: String,
    val anio: Int,
    val semestre: String,
    val comision: String,
    val descripcion: String
) {
    companion object {
        fun desdeModelo(curso: Curso): CursoResponseDTO = CursoResponseDTO(
            id = curso.id!!,
            materia = curso.materia,
            anio = curso.anio,
            semestre = curso.semestre,
            comision = curso.comision,
            descripcion = curso.descripcion ?: ""
        )
    }
}