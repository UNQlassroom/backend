package com.ar.edu.unq.unqlassroom.exception

class GitHubUsuarioNotFoundException(
    val username: String? = null,
    message: String = if (!username.isNullOrBlank()) {
        "El usuario '$username' no existe en GitHub"
    } else {
        "Usuario no encontrado en GitHub"
    },
    rawBody: String? = null,
) : GitHubNotFoundException(
    message = message,
    rawBody = rawBody,
    errorCode = "GITHUB_USER_NOT_FOUND",
)
