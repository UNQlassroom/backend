package com.ar.edu.unq.unqlassroom.exception

import org.springframework.http.HttpStatus

class GitHubServiceUnavailableException(
    message: String = "El servicio de GitHub no está disponible temporalmente",
    rawBody: String? = null,
) : GitHubApiException(
    statusCode = 503,
    message = "GitHub API request failed with status 503: $message",
    rawBody = rawBody,
    userFriendlyMessage = message,
    errorCode = "GITHUB_SERVICE_UNAVAILABLE",
    httpStatus = HttpStatus.SERVICE_UNAVAILABLE,
)
