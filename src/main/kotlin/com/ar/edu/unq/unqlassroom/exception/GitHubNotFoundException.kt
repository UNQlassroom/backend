package com.ar.edu.unq.unqlassroom.exception

import org.springframework.http.HttpStatus

open class GitHubNotFoundException(
    message: String = "El recurso solicitado no fue encontrado en GitHub",
    rawBody: String? = null,
    errorCode: String = "GITHUB_RESOURCE_NOT_FOUND",
) : GitHubApiException(
    statusCode = 404,
    message = "GitHub API request failed with status 404: $message",
    rawBody = rawBody,
    userFriendlyMessage = message,
    errorCode = errorCode,
    httpStatus = HttpStatus.NOT_FOUND,
)
