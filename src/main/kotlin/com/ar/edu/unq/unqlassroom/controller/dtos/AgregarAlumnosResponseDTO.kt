package com.ar.edu.unq.unqlassroom.controller.dtos

data class AgregarAlumnosResponseDTO(
    val cursoId: Long,
    val repoName: String,
    val alumnos: List<AlumnoMiembroDeUnCursoDTO>,
)
