package com.ar.edu.unq.unqlassroom.exception

class GitHubValidationException(
    message: String = "Los datos enviados no son válidos para la API de GitHub",
    rawBody: String? = null,
) : GitHubApiException(
    statusCode = 422,
    message = "GitHub API request failed with status 422: $message",
    rawBody = rawBody,
    userFriendlyMessage = message,
    errorCode = "GITHUB_VALIDATION_ERROR",
)
