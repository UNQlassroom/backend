package com.ar.edu.unq.unqlassroom.exception

class GitHubRepositorioNotFoundException(
    val repoName: String? = null,
    message: String = if (!repoName.isNullOrBlank()) {
        "El repositorio '$repoName' no fue encontrado en GitHub"
    } else {
        "Repositorio no encontrado en GitHub"
    },
    rawBody: String? = null,
) : GitHubNotFoundException(
    message = message,
    rawBody = rawBody,
    errorCode = "GITHUB_REPO_NOT_FOUND",
)
