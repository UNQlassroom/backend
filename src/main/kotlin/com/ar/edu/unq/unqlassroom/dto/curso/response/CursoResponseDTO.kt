package com.ar.edu.unq.unqlassroom.dto.curso.response

import com.ar.edu.unq.unqlassroom.model.Curso

data class CursoResponseDTO(
    val id: Long?,
    val materia: String,
    val anio: Int,
    val semestre: Int,
    val comision: Int,
    val descripcion: String? = null,
    val archivado: Boolean = false,
    val docentes: List<DocenteResponseDTO> = emptyList(),
) {
    companion object {
        fun desdeModelo(curso: Curso): CursoResponseDTO {
            return CursoResponseDTO(
                id = curso.id,
                materia = curso.materia,
                anio = curso.anio,
                semestre = curso.semestre,
                comision = curso.comision,
                descripcion = curso.descripcion,
                archivado = curso.archivado,
                docentes = curso.docentes.map { DocenteResponseDTO.desdeModelo(it) },
            )
        }
    }
}
