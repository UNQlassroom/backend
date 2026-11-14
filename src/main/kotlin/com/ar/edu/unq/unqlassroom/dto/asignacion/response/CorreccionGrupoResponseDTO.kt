package com.ar.edu.unq.unqlassroom.dto.asignacion.response

import com.ar.edu.unq.unqlassroom.dto.issue.response.IssueResponseDTO
import com.ar.edu.unq.unqlassroom.dto.usuario.response.UsuarioResponseDTO

data class CorreccionGrupoResponseDTO(
    val grupoId: Long,
    val nombre: String?,
    val integrantes: List<UsuarioResponseDTO>,
    val repoNombre: String,
    val repoHtmlUrl: String,
    val issues: List<IssueResponseDTO>,
)
