package com.ar.edu.unq.unqlassroom.dto.template.response

import com.ar.edu.unq.unqlassroom.model.Repositorio

data class TemplateResponseDTO(
    val name: String,
    val fullName: String,
    val htmlUrl: String,
    val description: String?,
    val id: Long? = null,
    val githubRepoId: Long? = null,
    val lastCommitSha: String? = null,
    val templateAprobado: Boolean = false,
) {
    companion object {

        fun desdeModelo(repo: Repositorio): TemplateResponseDTO = TemplateResponseDTO(
            id = repo.id,
            name = repo.nombre,
            fullName = repo.nombre,
            htmlUrl = repo.htmlUrl,
            description = repo.descripcion,
            githubRepoId = repo.githubRepoId,
            lastCommitSha = repo.ultimoCommitSha,
            templateAprobado = repo.templateAprobado,
        )
    }
}
