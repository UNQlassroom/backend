package com.ar.edu.unq.unqlassroom.exception

class GitHubForbiddenException(
    message: String = "La aplicación no tiene permisos suficientes en la organización o repositorio de GitHub",
    rawBody: String? = null,
) : GitHubApiException(
    statusCode = 403,
    message = "GitHub API request failed with status 403: $message",
    rawBody = rawBody,
    userFriendlyMessage = message,
    errorCode = "GITHUB_FORBIDDEN",
)
