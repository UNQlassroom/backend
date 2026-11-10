package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.Repositorio

interface TemplateService {
    fun crearTemplateRepository(
        name: String,
        description: String?,
        solicitanteUsername: String,
        repoAImportar: String? = null,
    ): Repositorio

    fun listarTemplates(solicitanteUsername: String): List<Repositorio>
}
