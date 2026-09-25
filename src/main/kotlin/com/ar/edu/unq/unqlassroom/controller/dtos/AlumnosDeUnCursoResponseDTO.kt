package com.ar.edu.unq.unqlassroom.controller.dtos

data class AlumnosDeUnCursoResponseDTO(
    val cursoId: Long,
    val alumnos: List<AlumnoMiembroDeUnCursoDTO>,
)
