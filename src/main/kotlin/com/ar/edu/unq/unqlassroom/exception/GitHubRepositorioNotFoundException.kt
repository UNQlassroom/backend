package com.ar.edu.unq.unqlassroom.exception

class GitHubRepositorioNotFoundException(
    val repoName: String? = null,
    message: String = if (!repoName.isNullOrBlank()) {
        "El repositorio '$repoName' no fue encontrado en GitHub"
    } else {
        "Repositorio no encontrado en GitHub"
    },
    rawBody: String? = null,
) : GitHubApiException(
    statusCode = 404,
    message = "GitHub API request failed with status 404: $message",
    rawBody = rawBody,
    userFriendlyMessage = message,
    errorCode = "GITHUB_REPO_NOT_FOUND",
)
