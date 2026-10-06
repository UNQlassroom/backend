package com.ar.edu.unq.unqlassroom.dto.asignacion.response

import com.ar.edu.unq.unqlassroom.dto.issue.response.IssueResponseDTO

data class CorreccionGrupoResponseDTO(
    val grupoId: Long,
    val nombre: String?,
    val integrantes: List<String>,
    val repoNombre: String,
    val repoHtmlUrl: String,
    val issues: List<IssueResponseDTO>,
)
