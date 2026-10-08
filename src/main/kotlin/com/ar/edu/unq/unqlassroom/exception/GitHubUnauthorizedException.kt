package com.ar.edu.unq.unqlassroom.exception

class GitHubUnauthorizedException(
    message: String = "Credenciales de GitHub inválidas o expiradas",
    rawBody: String? = null,
) : GitHubApiException(
    statusCode = 401,
    message = "GitHub API request failed with status 401: $message",
    rawBody = rawBody,
    userFriendlyMessage = message,
    errorCode = "GITHUB_UNAUTHORIZED",
)
