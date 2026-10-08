package com.ar.edu.unq.unqlassroom.exception

class GitHubUsuarioNotFoundException(
    val username: String? = null,
    message: String = if (!username.isNullOrBlank()) {
        "El usuario '$username' no existe en GitHub"
    } else {
        "Usuario no encontrado en GitHub"
    },
    rawBody: String? = null,
) : GitHubApiException(
    statusCode = 404,
    message = "GitHub API request failed with status 404: $message",
    rawBody = rawBody,
    userFriendlyMessage = message,
    errorCode = "GITHUB_USER_NOT_FOUND",
)
