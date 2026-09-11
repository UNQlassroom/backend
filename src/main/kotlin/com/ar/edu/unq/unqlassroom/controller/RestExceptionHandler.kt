package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.errors.BadRequestException
import com.ar.edu.unq.unqlassroom.errors.ConflictException
import com.ar.edu.unq.unqlassroom.errors.DuplicateResourceException
import com.ar.edu.unq.unqlassroom.errors.ForbiddenException
import com.ar.edu.unq.unqlassroom.errors.ResourceNotFoundException
import com.ar.edu.unq.unqlassroom.errors.UnauthorizedException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class RestExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFound(ex: ResourceNotFoundException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.NOT_FOUND, request)
    }

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.BAD_REQUEST, request)
    }

    @ExceptionHandler(DuplicateResourceException::class)
    fun handleDuplicateResource(ex: DuplicateResourceException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.CONFLICT, request)
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.CONFLICT, request)
    }

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.UNAUTHORIZED, request)
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.FORBIDDEN, request)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.BAD_REQUEST, request)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ApiError> {
        val errorMessage = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { "Datos de entrada inválidos" }
        return buildErrorResponse(errorMessage, HttpStatus.BAD_REQUEST, request)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.CONFLICT, request)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMissingRequestBody(ex: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse("El cuerpo de la solicitud es inválido o no fue provisto", HttpStatus.BAD_REQUEST, request)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException, request: HttpServletRequest): ResponseEntity<ApiError> {
        val status = HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR
        return buildErrorResponse(ex.reason ?: ex.message, status, request)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiError> {
        return buildErrorResponse(ex.message, HttpStatus.INTERNAL_SERVER_ERROR, request)
    }

    private fun buildErrorResponse(
        message: String?,
        status: HttpStatus,
        request: HttpServletRequest,
    ): ResponseEntity<ApiError> {
        val error = ApiError(
            status = status.value(),
            error = status.reasonPhrase,
            message = message,
            path = request.requestURI,
        )
        return ResponseEntity(error, status)
    }
}
