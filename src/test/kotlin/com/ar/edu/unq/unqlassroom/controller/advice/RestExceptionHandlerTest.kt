package com.ar.edu.unq.unqlassroom.controller.advice

import com.ar.edu.unq.unqlassroom.exception.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.server.ResponseStatusException

class RestExceptionHandlerTest {

    private lateinit var handler: RestExceptionHandler
    private lateinit var request: MockHttpServletRequest

    @BeforeEach
    fun setUp() {
        handler = RestExceptionHandler()
        request = MockHttpServletRequest()
        request.requestURI = "/api/test"
    }

    @Test
    fun `handleResourceNotFound returns 404`() {
        val ex = ResourceNotFoundException("Recurso no encontrado")
        val response = handler.handleResourceNotFound(ex, request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals(404, response.body?.status)
        assertEquals("Not Found", response.body?.error)
        assertEquals("Recurso no encontrado", response.body?.message)
        assertEquals("/api/test", response.body?.path)
    }

    @Test
    fun `handleBadRequest returns 400`() {
        val ex = BadRequestException("Solicitud inválida")
        val response = handler.handleBadRequest(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(400, response.body?.status)
        assertEquals("Bad Request", response.body?.error)
        assertEquals("Solicitud inválida", response.body?.message)
    }

    @Test
    fun `handleDuplicateResource returns 409`() {
        val ex = DuplicateResourceException("Recurso duplicado")
        val response = handler.handleDuplicateResource(ex, request)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(409, response.body?.status)
        assertEquals("Conflict", response.body?.error)
        assertEquals("Recurso duplicado", response.body?.message)
    }

    @Test
    fun `handleConflict returns 409`() {
        val ex = ConflictException("Conflicto detectado")
        val response = handler.handleConflict(ex, request)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(409, response.body?.status)
        assertEquals("Conflict", response.body?.error)
        assertEquals("Conflicto detectado", response.body?.message)
    }

    @Test
    fun `handleUnauthorized returns 401`() {
        val ex = UnauthorizedException("No autorizado")
        val response = handler.handleUnauthorized(ex, request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(401, response.body?.status)
        assertEquals("Unauthorized", response.body?.error)
        assertEquals("No autorizado", response.body?.message)
    }

    @Test
    fun `handleForbidden returns 403`() {
        val ex = ForbiddenException("Acceso denegado")
        val response = handler.handleForbidden(ex, request)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(403, response.body?.status)
        assertEquals("Forbidden", response.body?.error)
        assertEquals("Acceso denegado", response.body?.message)
    }

    @Test
    fun `handleIllegalArgument returns 400`() {
        val ex = IllegalArgumentException("Argumento inválido")
        val response = handler.handleIllegalArgument(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(400, response.body?.status)
        assertEquals("Bad Request", response.body?.error)
        assertEquals("Argumento inválido", response.body?.message)
    }

    @Test
    fun `handleMethodArgumentNotValidException with field errors returns combined messages`() {
        val target = Any()
        val bindingResult = BeanPropertyBindingResult(target, "target")
        bindingResult.addError(FieldError("target", "nombre", "no debe ser nulo"))
        bindingResult.addError(FieldError("target", "email", "formato inválido"))

        val method = this::class.java.methods.first()
        val param = MethodParameter(method, -1)
        val ex = MethodArgumentNotValidException(param, bindingResult)

        val response = handler.handleMethodArgumentNotValidException(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("nombre: no debe ser nulo, email: formato inválido", response.body?.message)
    }

    @Test
    fun `handleMethodArgumentNotValidException without field errors returns default message`() {
        val target = Any()
        val bindingResult = BeanPropertyBindingResult(target, "target")
        val method = this::class.java.methods.first()
        val param = MethodParameter(method, -1)
        val ex = MethodArgumentNotValidException(param, bindingResult)

        val response = handler.handleMethodArgumentNotValidException(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("Datos de entrada inválidos", response.body?.message)
    }

    @Test
    fun `handleIllegalState returns 409`() {
        val ex = IllegalStateException("Estado inconsistente")
        val response = handler.handleIllegalState(ex, request)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(409, response.body?.status)
        assertEquals("Conflict", response.body?.error)
        assertEquals("Estado inconsistente", response.body?.message)
    }

    @Test
    fun `handleMissingRequestBody returns 400 with default message`() {
        val inputMessage = org.springframework.mock.http.MockHttpInputMessage(ByteArray(0))
        val ex = HttpMessageNotReadableException("Body missing", inputMessage)
        val response = handler.handleMissingRequestBody(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("El cuerpo de la solicitud es inválido o no fue provisto", response.body?.message)
    }

    @Test
    fun `handleResponseStatusException resolves status and reason`() {
        val ex = ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Formato no aceptado")
        val response = handler.handleResponseStatusException(ex, request)

        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.statusCode)
        assertEquals(406, response.body?.status)
        assertEquals("Formato no aceptado", response.body?.message)
    }

    @Test
    fun `handleResponseStatusException with custom status code falls back to internal server error if unresolved`() {
        val customStatus = HttpStatusCode.valueOf(999)
        val ex = ResponseStatusException(customStatus, "Custom error")
        val response = handler.handleResponseStatusException(ex, request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Custom error", response.body?.message)
    }

    @Test
    fun `handleGenericException returns 500`() {
        val ex = RuntimeException("Falla imprevista")
        val response = handler.handleGenericException(ex, request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(500, response.body?.status)
        assertEquals("Falla imprevista", response.body?.message)
    }

    @Test
    fun `custom exception classes instantiate correctly`() {
        val ex1 = CursoNotFoundException()
        assertEquals("Curso no encontrado", ex1.message)

        val ex2 = AsignacionNotFoundException()
        assertEquals("Asignación no encontrada", ex2.message)

        val ex3 = UsuarioNotFoundException("Usuario con username: juan no encontrado")
        assertEquals("Usuario con username: juan no encontrado", ex3.message)

        val ex4 = CursoSinGitHubRepoAsociadoException()
        assertEquals("Curso sin repositorio de GitHub asociado", ex4.message)

        val ex5 = ResourceNotFoundException()
        assertNotNull(ex5)

        val ex6 = UsuarioNotFoundException()
        assertNotNull(ex6)
    }
}
