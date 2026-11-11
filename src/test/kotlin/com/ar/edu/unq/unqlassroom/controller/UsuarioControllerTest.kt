package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.controller.advice.RestExceptionHandler
import com.ar.edu.unq.unqlassroom.dto.usuario.request.ActualizarNombreRequestDTO
import com.ar.edu.unq.unqlassroom.exception.NombreUsuarioInvalidoException
import com.ar.edu.unq.unqlassroom.exception.UsuarioNotFoundException
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class UsuarioControllerTest {

    @Mock
    private lateinit var usuarioService: UsuarioService

    @InjectMocks
    private lateinit var usuarioController: UsuarioController

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(usuarioController)
            .setControllerAdvice(RestExceptionHandler())
            .build()
    }

    @Test
    fun `obtenerUsuarioActual returns 200 and user data`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val usuario = Usuario(
            id = 10L,
            username = "juanperez",
            esDocente = false,
            email = "juan@unq.edu.ar",
            nombreCompleto = "Juan Carlos Pérez",
        )

        `when`(usuarioService.obtenerUsuario("juanperez")).thenReturn(usuario)

        mockMvc.perform(
            get("/usuarios/me")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(10))
            .andExpect(jsonPath("$.username").value("juanperez"))
            .andExpect(jsonPath("$.esDocente").value(false))
            .andExpect(jsonPath("$.email").value("juan@unq.edu.ar"))
            .andExpect(jsonPath("$.nombreCompleto").value("Juan Carlos Pérez"))
    }

    @Test
    fun `actualizarNombreCompleto returns 200 and updated user data when request is valid`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarNombreRequestDTO(nombreCompleto = "Juan Carlos Pérez")
        val usuarioActualizado = Usuario(
            id = 10L,
            username = "juanperez",
            esDocente = false,
            email = "juan@unq.edu.ar",
            nombreCompleto = "Juan Carlos Pérez",
        )

        `when`(usuarioService.actualizarNombreCompleto("juanperez", "Juan Carlos Pérez"))
            .thenReturn(usuarioActualizado)

        mockMvc.perform(
            put("/usuarios/me/nombre")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nombreCompleto").value("Juan Carlos Pérez"))
            .andExpect(jsonPath("$.username").value("juanperez"))
    }

    @Test
    fun `actualizarNombreCompleto returns 400 when name is blank`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarNombreRequestDTO(nombreCompleto = "   ")

        mockMvc.perform(
            put("/usuarios/me/nombre")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
    }

    @Test
    fun `actualizarNombreCompleto returns 400 when name exceeds 35 chars`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarNombreRequestDTO(
            nombreCompleto = "Nombre Demasiado Largo Que Supera Los Treinta Y Cinco Caracteres"
        )

        mockMvc.perform(
            put("/usuarios/me/nombre")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
    }

    @Test
    fun `actualizarNombreCompleto returns 400 when name contains invalid characters`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarNombreRequestDTO(nombreCompleto = "Juan Pérez 123")

        mockMvc.perform(
            put("/usuarios/me/nombre")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
    }

    @Test
    fun `actualizarNombreCompleto returns 400 when service throws NombreUsuarioInvalidoException`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarNombreRequestDTO(nombreCompleto = "Juan")

        `when`(usuarioService.actualizarNombreCompleto("juanperez", "Juan"))
            .thenThrow(NombreUsuarioInvalidoException("Nombre inválido"))

        mockMvc.perform(
            put("/usuarios/me/nombre")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("NOMBRE_USUARIO_INVALIDO"))
            .andExpect(jsonPath("$.message").value("Nombre inválido"))
    }

    @Test
    fun `obtenerUsuarioActual returns 404 when user is not found`() {
        val auth = UsernamePasswordAuthenticationToken("fantasma", null)

        `when`(usuarioService.obtenerUsuario("fantasma"))
            .thenThrow(UsuarioNotFoundException("Usuario no encontrado: fantasma"))

        mockMvc.perform(
            get("/usuarios/me")
                .principal(auth)
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value("USUARIO_NOT_FOUND"))
    }
}
