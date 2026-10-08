package com.ar.edu.unq.unqlassroom.exception

open class GitHubApiException(
    val statusCode: Int,
    message: String,
    val rawBody: String? = null,
    val userFriendlyMessage: String? = null,
    val errorCode: String = "GITHUB_API_ERROR",
) : IllegalStateException(message)
