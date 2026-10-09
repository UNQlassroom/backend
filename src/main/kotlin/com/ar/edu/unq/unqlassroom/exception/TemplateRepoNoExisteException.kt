package com.ar.edu.unq.unqlassroom.exception

class TemplateRepoNoExisteException(
    templateRepoName: String? = null,
    message: String = if (templateRepoName != null) "El repositorio template '$templateRepoName' no existe en GitHub" else "El repositorio template no existe en GitHub",
) : ResourceNotFoundException(message, errorCode = "GITHUB_TEMPLATE_REPO_NOT_FOUND")
