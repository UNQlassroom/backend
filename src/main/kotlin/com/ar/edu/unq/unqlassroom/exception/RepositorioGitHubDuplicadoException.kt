package com.ar.edu.unq.unqlassroom.exception

import org.springframework.http.HttpStatus

class RepositorioGitHubDuplicadoException(
    val repoName: String? = null,
    message: String = if (!repoName.isNullOrBlank()) {
        "Ya existe un repositorio con el nombre '$repoName' en la organización de GitHub"
    } else {
        "Ya existe un repositorio con ese nombre en la organización de GitHub"
    },
    rawBody: String? = null,
) : GitHubApiException(
    statusCode = 422,
    message = "GitHub API request failed with status 422: $message",
    rawBody = rawBody,
    userFriendlyMessage = message,
    errorCode = "GITHUB_REPO_ALREADY_EXISTS",
    httpStatus = HttpStatus.CONFLICT,
)
