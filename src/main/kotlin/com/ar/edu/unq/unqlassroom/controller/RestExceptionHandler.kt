package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.errors.BadRequestException
import com.ar.edu.unq.unqlassroom.errors.ConflictException
import com.ar.edu.unq.unqlassroom.errors.DuplicateResourceException
import com.ar.edu.unq.unqlassroom.errors.ForbiddenException
import com.ar.edu.unq.unqlassroom.errors.ResourceNotFoundException
import com.ar.edu.unq.unqlassroom.errors.UnauthorizedException
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
    fun handleResourceNotFound(ex: ResourceNotFoundException): ResponseEntity<ApiError> {
        return buildErrorResponse(ex, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(BadRequestException::class)
    fun handleBadRequest(ex: BadRequestException): ResponseEntity<ApiError> {
        return buildErrorResponse(ex, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(DuplicateResourceException::class)
    fun handleDuplicateResource(ex: DuplicateResourceException): ResponseEntity<ApiError> {
        return buildErrorResponse(ex, HttpStatus.CONFLICT)
    }

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ResponseEntity<ApiError> {
        return buildErrorResponse(ex, HttpStatus.CONFLICT)
    }

    @ExceptionHandler(UnauthorizedException::class)
    fun handleUnauthorized(ex: UnauthorizedException): ResponseEntity<ApiError> {
        return buildErrorResponse(ex, HttpStatus.UNAUTHORIZED)
    }

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<ApiError> {
        return buildErrorResponse(ex, HttpStatus.FORBIDDEN)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ApiError> {
        return buildErrorResponse(ex, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValidException(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val errorMessage = ex.bindingResult.fieldErrors
            .joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
            .ifBlank { "Datos de entrada inválidos" }
        val error = ApiError(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = errorMessage
        )
        return ResponseEntity(error, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ApiError> {
        return buildErrorResponse(ex, HttpStatus.CONFLICT)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMissingRequestBody(ex: HttpMessageNotReadableException): ResponseEntity<ApiError> {
        val error = ApiError(
            status = HttpStatus.BAD_REQUEST.value(),
            error = HttpStatus.BAD_REQUEST.reasonPhrase,
            message = "El cuerpo de la solicitud es inválido o no fue provisto"
        )
        return ResponseEntity(error, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(ex: ResponseStatusException): ResponseEntity<ApiError> {
        val status = HttpStatus.resolve(ex.statusCode.value()) ?: HttpStatus.INTERNAL_SERVER_ERROR
        val error = ApiError(
            status = status.value(),
            error = status.reasonPhrase,
            message = ex.reason ?: ex.message
        )
        return ResponseEntity(error, status)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ApiError> {
        return buildErrorResponse(ex, HttpStatus.INTERNAL_SERVER_ERROR)
    }

    private fun buildErrorResponse(ex: Exception, status: HttpStatus): ResponseEntity<ApiError> {
        val error = ApiError(
            status = status.value(),
            error = status.reasonPhrase,
            message = ex.message
        )
        return ResponseEntity(error, status)
    }
}
