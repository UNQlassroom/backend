package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.Repositorio

interface TemplateStandardizationService {
    fun asegurarTemplateEstandarizado(templateRepoName: String): Repositorio
    fun analizarYParchearPom(pomContent: String): String?
    fun analizarYParchearGradle(gradleContent: String, esKotlinDsl: Boolean = false): String?
    fun construirWorkflowCI(): String
}

