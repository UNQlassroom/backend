package com.ar.edu.unq.unqlassroom.exception

class GitHubRateLimitException(
    message: String = "Se ha superado el límite de peticiones a la API de GitHub. Intente nuevamente en unos minutos",
    rawBody: String? = null,
) : GitHubApiException(
    statusCode = 403,
    message = "GitHub API request failed with status 403: $message",
    rawBody = rawBody,
    userFriendlyMessage = message,
    errorCode = "GITHUB_RATE_LIMIT_EXCEEDED",
)
