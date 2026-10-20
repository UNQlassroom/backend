package com.ar.edu.unq.unqlassroom.dto.template.response

import com.ar.edu.unq.unqlassroom.model.Repositorio

data class TemplateResponseDTO(
    val name: String,
    val fullName: String,
    val htmlUrl: String,
    val description: String?,
) {
    companion object {

        fun desdeModelo(repo: Repositorio): TemplateResponseDTO = TemplateResponseDTO(
            name = repo.nombre,
            fullName = repo.nombre,
            htmlUrl = repo.htmlUrl,
            description = repo.descripcion,
        )
    }
}
