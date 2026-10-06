package com.ar.edu.unq.unqlassroom.dto.curso.response

import com.ar.edu.unq.unqlassroom.model.Inscripcion

data class CursoAlumnosResponseDTO(
    val cursoId: Long,
    val alumnos: List<AlumnoResponseDTO>,
) {
    companion object {
        fun desdeModelo(cursoId: Long, inscripciones: List<Inscripcion>): CursoAlumnosResponseDTO =
            CursoAlumnosResponseDTO(
                cursoId = cursoId,
                alumnos = inscripciones.map { AlumnoResponseDTO.desdeModelo(it) },
            )
    }
}
