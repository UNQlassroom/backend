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
    fun `handleAuthentication returns 401 and UNAUTHORIZED`() {
        val ex = org.springframework.security.authentication.BadCredentialsException("Bad credentials")
        val response = handler.handleAuthentication(ex, request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(401, response.body?.status)
        assertEquals("Unauthorized", response.body?.error)
        assertEquals("No autorizado", response.body?.message)
        assertEquals("UNAUTHORIZED", response.body?.errorCode)
    }

    @Test
    fun `handleAccessDenied returns 403 and ACCESS_DENIED`() {
        val ex = org.springframework.security.access.AccessDeniedException("Access denied")
        val response = handler.handleAccessDenied(ex, request)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals(403, response.body?.status)
        assertEquals("Forbidden", response.body?.error)
        assertEquals("Acceso denegado: se requieren permisos de docente", response.body?.message)
        assertEquals("ACCESS_DENIED", response.body?.errorCode)
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
    fun `handleUnauthorized with GitHubOAuthException returns 401 and GITHUB_OAUTH_ERROR`() {
        val ex = GitHubOAuthException("Error de comunicación con GitHub OAuth: timeout")
        val response = handler.handleUnauthorized(ex, request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals(401, response.body?.status)
        assertEquals("Unauthorized", response.body?.error)
        assertEquals("Error de comunicación con GitHub OAuth: timeout", response.body?.message)
        assertEquals("GITHUB_OAUTH_ERROR", response.body?.errorCode)
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
    fun `handleIllegalArgument returns 400 and INVALID_ARGUMENT`() {
        val ex = IllegalArgumentException("Argumento inválido")
        val response = handler.handleIllegalArgument(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(400, response.body?.status)
        assertEquals("Bad Request", response.body?.error)
        assertEquals("Argumento inválido", response.body?.message)
        assertEquals("INVALID_ARGUMENT", response.body?.errorCode)
    }

    @Test
    fun `handleMethodArgumentNotValidException with field errors returns combined messages and VALIDATION_ERROR`() {
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
        assertEquals("VALIDATION_ERROR", response.body?.errorCode)
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
        assertEquals("VALIDATION_ERROR", response.body?.errorCode)
    }

    @Test
    fun `handleGitHubApi with RepositorioGitHubDuplicado returns 409 and GITHUB_REPO_ALREADY_EXISTS`() {
        val ex = RepositorioGitHubDuplicadoException("repo-1")
        val response = handler.handleGitHubApi(ex, request)

        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals(409, response.body?.status)
        assertEquals("Conflict", response.body?.error)
        assertEquals("GITHUB_REPO_ALREADY_EXISTS", response.body?.errorCode)
        assertEquals("Ya existe un repositorio con el nombre 'repo-1' en la organización de GitHub", response.body?.message)
    }

    @Test
    fun `handleGitHubApi with GitHubRateLimit returns 429 and GITHUB_RATE_LIMIT_EXCEEDED`() {
        val ex = GitHubRateLimitException()
        val response = handler.handleGitHubApi(ex, request)

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.statusCode)
        assertEquals(429, response.body?.status)
        assertEquals("Too Many Requests", response.body?.error)
        assertEquals("GITHUB_RATE_LIMIT_EXCEEDED", response.body?.errorCode)
        assertEquals("Se ha superado el límite de peticiones a la API de GitHub. Intente nuevamente en unos minutos", response.body?.message)
    }

    @Test
    fun `handleGitHubApi with GitHubUnauthorized returns 401 and GITHUB_UNAUTHORIZED`() {
        val ex = GitHubUnauthorizedException()
        val response = handler.handleGitHubApi(ex, request)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("GITHUB_UNAUTHORIZED", response.body?.errorCode)
    }

    @Test
    fun `handleGitHubApi with GitHubForbidden returns 403 and GITHUB_FORBIDDEN`() {
        val ex = GitHubForbiddenException()
        val response = handler.handleGitHubApi(ex, request)

        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("GITHUB_FORBIDDEN", response.body?.errorCode)
    }

    @Test
    fun `handleGitHubApi with GitHubNotFound returns 404 and GITHUB_RESOURCE_NOT_FOUND`() {
        val ex = GitHubNotFoundException()
        val response = handler.handleGitHubApi(ex, request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("GITHUB_RESOURCE_NOT_FOUND", response.body?.errorCode)
        assertEquals("El recurso solicitado no fue encontrado en GitHub", response.body?.message)
    }

    @Test
    fun `handleGitHubApi with GitHubUsuarioNotFound returns 404 and GITHUB_USER_NOT_FOUND`() {
        val ex = GitHubUsuarioNotFoundException("alumno99")
        val response = handler.handleGitHubApi(ex, request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("GITHUB_USER_NOT_FOUND", response.body?.errorCode)
        assertEquals("El usuario 'alumno99' no existe en GitHub", response.body?.message)
    }

    @Test
    fun `handleGitHubApi with GitHubRepositorioNotFound returns 404 and GITHUB_REPO_NOT_FOUND`() {
        val ex = GitHubRepositorioNotFoundException("repo-inexistente")
        val response = handler.handleGitHubApi(ex, request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("GITHUB_REPO_NOT_FOUND", response.body?.errorCode)
        assertEquals("El repositorio 'repo-inexistente' no fue encontrado en GitHub", response.body?.message)
    }

    @Test
    fun `handleGitHubApi with GitHubTemplateRepoNotFound returns 404 and GITHUB_TEMPLATE_REPO_NOT_FOUND`() {
        val ex = GitHubTemplateRepoNotFoundException("template-base")
        val response = handler.handleGitHubApi(ex, request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("GITHUB_TEMPLATE_REPO_NOT_FOUND", response.body?.errorCode)
        assertEquals("El repositorio template 'template-base' no existe o no está configurado como template en GitHub", response.body?.message)
    }

    @Test
    fun `handleGitHubApi with GitHubValidation returns 400 and GITHUB_VALIDATION_ERROR`() {
        val ex = GitHubValidationException("Nombre de repo demasiado largo")
        val response = handler.handleGitHubApi(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("GITHUB_VALIDATION_ERROR", response.body?.errorCode)
    }

    @Test
    fun `handleGitHubApi with GitHubServiceUnavailable returns 503 and GITHUB_SERVICE_UNAVAILABLE`() {
        val ex = GitHubServiceUnavailableException()
        val response = handler.handleGitHubApi(ex, request)

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.statusCode)
        assertEquals("GITHUB_SERVICE_UNAVAILABLE", response.body?.errorCode)
    }

    @Test
    fun `handleBadRequest with UsuariosGitHubNoEncontrados returns 400 and GITHUB_USERS_NOT_FOUND`() {
        val ex = UsuariosGitHubNoEncontradosException(listOf("user1", "user2"))
        val response = handler.handleBadRequest(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("GITHUB_USERS_NOT_FOUND", response.body?.errorCode)
    }

    @Test
    fun `handleResourceNotFound with TemplateRepoNoExiste returns 404 and GITHUB_TEMPLATE_REPO_NOT_FOUND`() {
        val ex = TemplateRepoNoExisteException("tmpl")
        val response = handler.handleResourceNotFound(ex, request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("GITHUB_TEMPLATE_REPO_NOT_FOUND", response.body?.errorCode)
    }

    @Test
    fun `handleGitHubApi with generic GitHubApiException defaults to 502 BAD_GATEWAY`() {
        val ex = GitHubApiException(500, "Error interno", userFriendlyMessage = "Error de comunicación con GitHub")
        val response = handler.handleGitHubApi(ex, request)

        assertEquals(HttpStatus.BAD_GATEWAY, response.statusCode)
        assertEquals("GITHUB_API_ERROR", response.body?.errorCode)
        assertEquals("Error de comunicación con GitHub", response.body?.message)
    }

    @Test
    fun `handleGitHubApi with generic GitHubApiException uses explicit httpStatus when provided`() {
        val ex = GitHubApiException(
            statusCode = 404,
            message = "Not found",
            userFriendlyMessage = "El recurso solicitado no fue encontrado en GitHub",
            httpStatus = HttpStatus.NOT_FOUND,
        )
        val response = handler.handleGitHubApi(ex, request)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("GITHUB_API_ERROR", response.body?.errorCode)
    }

    @Test
    fun `handleIllegalState returns 500 and INTERNAL_SERVER_ERROR`() {
        val ex = IllegalStateException("Estado inconsistente")
        val response = handler.handleIllegalState(ex, request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(500, response.body?.status)
        assertEquals("Internal Server Error", response.body?.error)
        assertEquals("Ocurrió un error interno en el servidor", response.body?.message)
        assertEquals("INTERNAL_SERVER_ERROR", response.body?.errorCode)
    }

    @Test
    fun `handleMissingRequestBody returns 400 with default message and INVALID_REQUEST_BODY`() {
        val inputMessage = org.springframework.mock.http.MockHttpInputMessage(ByteArray(0))
        val ex = HttpMessageNotReadableException("Body missing", inputMessage)
        val response = handler.handleMissingRequestBody(ex, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals("El cuerpo de la solicitud es inválido o no fue provisto", response.body?.message)
        assertEquals("INVALID_REQUEST_BODY", response.body?.errorCode)
    }

    @Test
    fun `handleResponseStatusException resolves status and reason`() {
        val ex = ResponseStatusException(HttpStatus.NOT_ACCEPTABLE, "Formato no aceptado")
        val response = handler.handleResponseStatusException(ex, request)

        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.statusCode)
        assertEquals(406, response.body?.status)
        assertEquals("Formato no aceptado", response.body?.message)
        assertEquals("NOT_ACCEPTABLE", response.body?.errorCode)
    }

    @Test
    fun `handleResponseStatusException with custom status code falls back to internal server error if unresolved`() {
        val customStatus = HttpStatusCode.valueOf(999)
        val ex = ResponseStatusException(customStatus, "Custom error")
        val response = handler.handleResponseStatusException(ex, request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("Custom error", response.body?.message)
        assertEquals("INTERNAL_SERVER_ERROR", response.body?.errorCode)
    }

    @Test
    fun `handleGenericException returns 500 and sanitized message`() {
        val ex = RuntimeException("Falla imprevista con detalles sensibles")
        val response = handler.handleGenericException(ex, request)

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals(500, response.body?.status)
        assertEquals("Ocurrió un error interno en el servidor", response.body?.message)
        assertEquals("INTERNAL_SERVER_ERROR", response.body?.errorCode)
    }

    @Test
    fun `domain exceptions pass their respective errorCode to ApiError`() {
        val ex1 = CursoNotFoundException()
        val res1 = handler.handleResourceNotFound(ex1, request)
        assertEquals(HttpStatus.NOT_FOUND, res1.statusCode)
        assertEquals("CURSO_NOT_FOUND", res1.body?.errorCode)

        val ex2 = AsignacionVencidaException()
        val res2 = handler.handleBadRequest(ex2, request)
        assertEquals(HttpStatus.BAD_REQUEST, res2.statusCode)
        assertEquals("ASIGNACION_VENCIDA", res2.body?.errorCode)

        val ex3 = SinPermisoAccesoCursoException()
        val res3 = handler.handleForbidden(ex3, request)
        assertEquals(HttpStatus.FORBIDDEN, res3.statusCode)
        assertEquals("SIN_PERMISO_ACCESO_CURSO", res3.body?.errorCode)

        val ex4 = AsignacionDuplicadaException("TP1")
        val res4 = handler.handleDuplicateResource(ex4, request)
        assertEquals(HttpStatus.CONFLICT, res4.statusCode)
        assertEquals("ASIGNACION_DUPLICADA", res4.body?.errorCode)

        val ex5 = NombreGrupoDuplicadoException()
        val res5 = handler.handleDuplicateResource(ex5, request)
        assertEquals(HttpStatus.CONFLICT, res5.statusCode)
        assertEquals("NOMBRE_GRUPO_DUPLICADO", res5.body?.errorCode)

        val ex6 = CursoDuplicadoException("SO", 2026, 1, 1)
        val res6 = handler.handleDuplicateResource(ex6, request)
        assertEquals(HttpStatus.CONFLICT, res6.statusCode)
        assertEquals("CURSO_DUPLICADO", res6.body?.errorCode)
    }

    @Test
    fun `custom exception classes instantiate correctly`() {
        val ex1 = CursoNotFoundException()
        assertEquals("Curso no encontrado", ex1.message)
        assertEquals("CURSO_NOT_FOUND", ex1.errorCode)

        val ex2 = AsignacionNotFoundException()
        assertEquals("Asignación no encontrada", ex2.message)
        assertEquals("ASIGNACION_NOT_FOUND", ex2.errorCode)

        val ex3 = UsuarioNotFoundException("Usuario con username: juan no encontrado")
        assertEquals("Usuario con username: juan no encontrado", ex3.message)
        assertEquals("USUARIO_NOT_FOUND", ex3.errorCode)

        val ex4 = CursoSinGitHubRepoAsociadoException()
        assertEquals("Curso sin repositorio de GitHub asociado", ex4.message)
        assertEquals("CURSO_SIN_GITHUB_REPO_ASOCIADO", ex4.errorCode)

        val ex5 = ResourceNotFoundException()
        assertNotNull(ex5)

        val ex6 = UsuarioNotFoundException()
        assertNotNull(ex6)
    }
}
