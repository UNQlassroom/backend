package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.controller.advice.RestExceptionHandler
import com.ar.edu.unq.unqlassroom.dto.usuario.request.ActualizarDatosRequestDTO
import com.ar.edu.unq.unqlassroom.dto.usuario.request.ActualizarNombreRequestDTO
import com.ar.edu.unq.unqlassroom.exception.DniDuplicadoException
import com.ar.edu.unq.unqlassroom.exception.EmailDuplicadoException
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
    fun `obtenerUsuarioActual returns 200 and user data including email and dni`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val usuario = Usuario(
            id = 10L,
            username = "juanperez",
            email = "juan@unq.com.ar",
            dni = "12345678",
            nombreCompleto = "García Lorca, Juan Manuel",
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
            .andExpect(jsonPath("$.email").value("juan@unq.com.ar"))
            .andExpect(jsonPath("$.dni").value("12345678"))
            .andExpect(jsonPath("$.nombreCompleto").value("García Lorca, Juan Manuel"))
    }

    @Test
    fun `actualizarDatos returns 200 and updated user data`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarDatosRequestDTO(
            nombre = "Juan Manuel",
            apellido = "García Lorca",
            email = "juan@correo.com",
            dni = "35123456",
        )
        val usuarioActualizado = Usuario(
            id = 10L,
            username = "juanperez",
            email = "juan@correo.com",
            dni = "35123456",
            nombreCompleto = "García Lorca, Juan Manuel",
        )

        `when`(usuarioService.actualizarDatos(
            username = "juanperez",
            nombre = "Juan Manuel",
            apellido = "García Lorca",
            email = "juan@correo.com",
            dni = "35123456",
        )).thenReturn(usuarioActualizado)

        mockMvc.perform(
            put("/usuarios/me/datos")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value("juan@correo.com"))
            .andExpect(jsonPath("$.dni").value("35123456"))
            .andExpect(jsonPath("$.nombreCompleto").value("García Lorca, Juan Manuel"))
            .andExpect(jsonPath("$.username").value("juanperez"))
    }

    @Test
    fun `actualizarDatos returns 400 when email format does not end with com or com ar`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarDatosRequestDTO(
            nombre = "Juan",
            apellido = "Pérez",
            email = "juan@unq.edu.ar",
            dni = "12345678",
        )

        mockMvc.perform(
            put("/usuarios/me/datos")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
    }

    @Test
    fun `actualizarDatos returns 400 when dni has invalid length`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarDatosRequestDTO(
            nombre = "Juan",
            apellido = "Pérez",
            email = "juan@correo.com",
            dni = "123456",
        )

        mockMvc.perform(
            put("/usuarios/me/datos")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
    }

    @Test
    fun `actualizarDatos returns 409 when email is already registered in another account`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarDatosRequestDTO(
            nombre = "Juan",
            apellido = "Pérez",
            email = "repetido@correo.com",
            dni = "12345678",
        )

        `when`(usuarioService.actualizarDatos(
            username = "juanperez",
            nombre = "Juan",
            apellido = "Pérez",
            email = "repetido@correo.com",
            dni = "12345678",
        )).thenThrow(EmailDuplicadoException())

        mockMvc.perform(
            put("/usuarios/me/datos")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value("EMAIL_DUPLICADO"))
    }

    @Test
    fun `actualizarDatos returns 409 when dni is already registered in another account`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarDatosRequestDTO(
            nombre = "Juan",
            apellido = "Pérez",
            email = "libre@correo.com",
            dni = "40123456",
        )

        `when`(usuarioService.actualizarDatos(
            username = "juanperez",
            nombre = "Juan",
            apellido = "Pérez",
            email = "libre@correo.com",
            dni = "40123456",
        )).thenThrow(DniDuplicadoException())

        mockMvc.perform(
            put("/usuarios/me/datos")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.errorCode").value("DNI_DUPLICADO"))
    }

    @Test
    fun `actualizarNombreCompleto returns 200 and formatted Apellidos, Nombres when given nombre and apellido`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarNombreRequestDTO(
            nombre = "Juan Manuel",
            apellido = "García Lorca",
        )
        val usuarioActualizado = Usuario(
            id = 10L,
            username = "juanperez",
            email = "juan@unq.com.ar",
            nombreCompleto = "García Lorca, Juan Manuel",
        )

        `when`(usuarioService.actualizarNombreCompleto("juanperez", "Juan Manuel", "García Lorca"))
            .thenReturn(usuarioActualizado)

        mockMvc.perform(
            put("/usuarios/me/nombre")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nombreCompleto").value("García Lorca, Juan Manuel"))
            .andExpect(jsonPath("$.username").value("juanperez"))
    }

    @Test
    fun `actualizarNombreCompleto returns 200 when names contain accents, apostrophes and hyphens`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarNombreRequestDTO(
            nombre = "Martín",
            apellido = "D'Angelo-García",
        )
        val nombreEsperado = "D'Angelo-García, Martín"
        val usuarioActualizado = Usuario(
            id = 10L,
            username = "juanperez",
            email = "juan@unq.com.ar",
            nombreCompleto = nombreEsperado,
        )

        `when`(usuarioService.actualizarNombreCompleto("juanperez", "Martín", "D'Angelo-García"))
            .thenReturn(usuarioActualizado)

        mockMvc.perform(
            put("/usuarios/me/nombre")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nombreCompleto").value(nombreEsperado))
    }

    @Test
    fun `actualizarNombreCompleto returns 200 when names contain enye tanto mayuscula como minuscula`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarNombreRequestDTO(
            nombre = "Iñaki",
            apellido = "Ñúñez Peña",
        )
        val nombreEsperado = "Ñúñez Peña, Iñaki"
        val usuarioActualizado = Usuario(
            id = 10L,
            username = "juanperez",
            email = "juan@unq.edu.ar",
            nombreCompleto = nombreEsperado,
        )

        `when`(usuarioService.actualizarNombreCompleto("juanperez", "Iñaki", "Ñúñez Peña"))
            .thenReturn(usuarioActualizado)

        mockMvc.perform(
            put("/usuarios/me/nombre")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.nombreCompleto").value(nombreEsperado))
    }

    @Test
    fun `actualizarNombreCompleto returns 400 when nombre is blank`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarNombreRequestDTO(nombre = "   ", apellido = "García Lorca")

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
    fun `actualizarNombreCompleto returns 400 when apellido is blank`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarNombreRequestDTO(nombre = "Juan Manuel", apellido = "   ")

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
    fun `actualizarNombreCompleto returns 400 when nombre contains invalid characters`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarNombreRequestDTO(nombre = "Juan 123", apellido = "Pérez")

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
    fun `actualizarNombreCompleto returns 400 when apellido contains invalid characters`() {
        val auth = UsernamePasswordAuthenticationToken("juanperez", null)
        val request = ActualizarNombreRequestDTO(nombre = "Juan", apellido = "Pérez @#$")

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
        val request = ActualizarNombreRequestDTO(nombre = "Juan", apellido = "Pérez")

        `when`(usuarioService.actualizarNombreCompleto("juanperez", "Juan", "Pérez"))
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
