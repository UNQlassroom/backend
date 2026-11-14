package com.ar.edu.unq.unqlassroom.exception

import org.springframework.http.HttpStatus

/**
 * Error genérico de la API de GitHub.
 *
 * - [statusCode]: status HTTP original que respondió GitHub (para logs/auditoría).
 * - [httpStatus]: status HTTP que nuestra API le devuelve al frontend.
 *
 * Las subclases definen explícitamente su [httpStatus] y [errorCode].
 * Si se lanza esta clase directamente (error no catalogado), se responde 502 BAD_GATEWAY.
 */
open class GitHubApiException(
    val statusCode: Int,
    message: String,
    val rawBody: String? = null,
    val userFriendlyMessage: String? = null,
    val errorCode: String = "GITHUB_API_ERROR",
    val httpStatus: HttpStatus = HttpStatus.BAD_GATEWAY,
) : RuntimeException(message)
