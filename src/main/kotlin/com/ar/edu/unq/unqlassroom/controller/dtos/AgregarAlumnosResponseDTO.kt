package com.ar.edu.unq.unqlassroom.controller.dtos

data class AlumnoTeamMembershipDTO(
    val username: String,
    val role: String,
    val state: String,
)

data class AgregarAlumnosResponseDTO(
    val cursoId: Long,
    val teamSlug: String,
    val alumnos: List<AlumnoTeamMembershipDTO>,
)
