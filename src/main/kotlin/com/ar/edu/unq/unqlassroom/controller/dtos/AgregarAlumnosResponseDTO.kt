package com.ar.edu.unq.unqlassroom.controller.dtos

data class AgregarAlumnosResponseDTO(
    val cursoId: Long,
    val teamSlug: String,
    val alumnos: List<AlumnoTeamMembershipDTO>,
)
