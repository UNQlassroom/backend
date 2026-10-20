package com.ar.edu.unq.unqlassroom.dto.issue.response

import com.ar.edu.unq.unqlassroom.model.EstadoIssue

data class IssueResponseDTO(
    val numero: Int,
    val titulo: String,
    val htmlUrl: String,
    val autor: String,
    val estado: EstadoIssue,
    val tieneCommitsPosteriores: Boolean,
    val cantComentarios: Int,
    val fechaCreacion: String,
    val fechaActualizacion: String,
    val fechaCierre: String? = null,
)
