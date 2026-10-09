package com.ar.edu.unq.unqlassroom.exception

class GitHubTemplateRepoNotFoundException(
    val templateRepoName: String? = null,
    message: String = if (!templateRepoName.isNullOrBlank()) {
        "El repositorio template '$templateRepoName' no existe o no está configurado como template en GitHub"
    } else {
        "El repositorio template no existe o no está configurado como template en GitHub"
    },
    rawBody: String? = null,
) : GitHubNotFoundException(
    message = message,
    rawBody = rawBody,
    errorCode = "GITHUB_TEMPLATE_REPO_NOT_FOUND",
)
