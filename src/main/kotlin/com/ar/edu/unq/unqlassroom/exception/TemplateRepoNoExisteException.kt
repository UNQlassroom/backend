package com.ar.edu.unq.unqlassroom.exception

class TemplateRepoNoExisteException(
    templateRepoName: String? = null,
    message: String = if (templateRepoName != null) "El repositorio template '$templateRepoName' no existe en GitHub" else "El repositorio template no existe en GitHub",
) : BadRequestException(message)
