package com.ar.edu.unq.unqlassroom.controller.dtos

data class AlumnoMiembroDeUnCursoDTO(
    val username: String,
    val role: String,
    val state: String,
    val repositorio: RepositorioDTO? = null,
)
