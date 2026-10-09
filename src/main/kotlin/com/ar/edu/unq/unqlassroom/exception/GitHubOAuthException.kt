package com.ar.edu.unq.unqlassroom.exception

class GitHubOAuthException(
    message: String = "Error al autenticar con GitHub OAuth",
) : UnauthorizedException(message, errorCode = "GITHUB_OAUTH_ERROR")
