package com.ar.edu.unq.unqlassroom.exception

import org.springframework.http.HttpStatus

class GitHubRateLimitException(
    statusCode: Int = 429,
    message: String = "Se ha superado el límite de peticiones a la API de GitHub. Intente nuevamente en unos minutos",
    rawBody: String? = null,
) : GitHubApiException(
    statusCode = statusCode,
    message = "GitHub API request failed with status $statusCode: $message",
    rawBody = rawBody,
    userFriendlyMessage = message,
    errorCode = "GITHUB_RATE_LIMIT_EXCEEDED",
    httpStatus = HttpStatus.TOO_MANY_REQUESTS,
)
