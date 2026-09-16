package com.ar.edu.unq.unqlassroom.controller.dtos

data class ObtenerAlumnosResponseDTO(
    val cursoId: Long,
    val repoName: String,
    val alumnos: List<AlumnoMiembroDeUnCursoDTO>,
)