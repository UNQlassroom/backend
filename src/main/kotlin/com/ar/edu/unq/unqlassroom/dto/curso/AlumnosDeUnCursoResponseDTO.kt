package com.ar.edu.unq.unqlassroom.dto.curso

data class AlumnosDeUnCursoResponseDTO(
    val cursoId: Long,
    val alumnos: List<AlumnoMiembroDeUnCursoDTO>,
)
