package com.ar.edu.unq.unqlassroom.dto.asignacion

import com.ar.edu.unq.unqlassroom.dto.issue.IssueResponseDTO

data class CorreccionGrupoResponseDTO(
    val grupoId: Long,
    val nombre: String?,
    val integrantes: List<String>,
    val repoNombre: String,
    val repoHtmlUrl: String,
    val issues: List<IssueResponseDTO>,
)
