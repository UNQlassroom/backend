package com.ar.edu.unq.unqlassroom.exception

import org.springframework.http.HttpStatus

class GitHubTemplateRepoNotFoundException(
    val templateRepoName: String? = null,
    message: String = if (!templateRepoName.isNullOrBlank()) {
        "El repositorio template '$templateRepoName' no existe o no está configurado como template en GitHub"
    } else {
        "El repositorio template no existe o no está configurado como template en GitHub"
    },
    rawBody: String? = null,
) : GitHubApiException(
    statusCode = 404,
    message = "GitHub API request failed with status 404: $message",
    rawBody = rawBody,
    userFriendlyMessage = message,
    errorCode = "GITHUB_TEMPLATE_REPO_NOT_FOUND",
    httpStatus = HttpStatus.NOT_FOUND,
)
