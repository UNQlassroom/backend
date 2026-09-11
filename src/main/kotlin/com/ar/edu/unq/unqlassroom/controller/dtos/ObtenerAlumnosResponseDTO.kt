package com.ar.edu.unq.unqlassroom.controller.dtos

data class ObtenerAlumnosResponseDTO(
    val cursoId: Long,
    val teamSlug: String,
    val alumnos: List<AlumnoTeamMemberDTO>,
)