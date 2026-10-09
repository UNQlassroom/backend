package com.ar.edu.unq.unqlassroom.controller.advice

import com.ar.edu.unq.unqlassroom.exception.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class RestExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(ex: ResourceNotFoundException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.NOT_FOUND, request, errorCode = ex.errorCode)
    }

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.BAD_REQUEST, request, errorCode = ex.errorCode)
    }

    @ExceptionHandler(DuplicateResourceException::class)
    fun handleDuplicateResource(ex: DuplicateResourceException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.CONFLICT, request, errorCode = ex.errorCode)
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.CONFLICT, request, errorCode = ex.errorCode)
    }

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.UNAUTHORIZED, request, errorCode = ex.errorCode)
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.FORBIDDEN, request, errorCode = ex.errorCode)
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse("No autorizado", HttpStatus.UNAUTHORIZED, request, errorCode = "UNAUTHORIZED")
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(
            "Acceso denegado: se requieren permisos de docente",
            HttpStatus.FORBIDDEN,
            request,
            errorCode = "ACCESS_DENIED",
        )
    }

    @ExceptionHandler(GitHubApiException::class)
    fun handleGitHubApi(ex: GitHubApiException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.userFriendlyMessage ?: "No se pudo completar la operación con GitHub", ex.httpStatus, request, errorCode = ex.errorCode)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.BAD_REQUEST, request, errorCode = "INVALID_ARGUMENT")
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiError> {
        val errorMessage = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { "Datos de entrada inválidos" }
        return buildErrorResponse(errorMessage, HttpStatus.BAD_REQUEST, request, errorCode = "VALIDATION_ERROR")
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(
            "Ocurrió un error interno en el servidor",
            HttpStatus.INTERNAL_SERVER_ERROR,
            request,
            errorCode = "INTERNAL_SERVER_ERROR",
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMissingRequestBody(ex: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(
            "El cuerpo de la solicitud es inválido o no fue provisto",
            HttpStatus.BAD_REQUEST,
            request,
            errorCode = "INVALID_REQUEST_BODY",
        )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException, request: HttpServletRequest): ResponseEntity<ApiError> {
        val status = HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR
        return buildErrorResponse(ex.reason ?: ex.message, status, request, errorCode = status.name)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(
            "Ocurrió un error interno en el servidor",
            HttpStatus.INTERNAL_SERVER_ERROR,
            request,
            errorCode = "INTERNAL_SERVER_ERROR",
        )
    }

    private fun buildErrorResponse(
        message: String?,
        status: HttpStatus,
        request: HttpServletRequest,
        errorCode: String? = null,
    ): ResponseEntity<ApiError> {
        val error = ApiError(
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            errorCode = errorCode,
            path = request.requestURI,
        )
        return ResponseEntity(error, status)
    }
}
