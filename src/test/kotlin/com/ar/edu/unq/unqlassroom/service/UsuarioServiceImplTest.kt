package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.exception.*
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.impl.UsuarioServiceImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers.any
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class UsuarioServiceImplTest {

    @Mock
    private lateinit var usuarioRepository: UsuarioRepository

    @InjectMocks
    private lateinit var usuarioService: UsuarioServiceImpl

    @Test
    fun `obtenerDocente returns user when user exists`() {
        val docente = Usuario(id = 1L, username = "profe")
        `when`(usuarioRepository.findByUsername("profe")).thenReturn(docente)

        val result = usuarioService.obtenerDocente("profe")

        assertNotNull(result)
        assertEquals("profe", result.username)
    }

    @Test
    fun `obtenerDocente throws UsuarioNotFoundException when user does not exist`() {
        `when`(usuarioRepository.findByUsername("fantasma")).thenReturn(null)

        val exception = assertThrows<UsuarioNotFoundException> {
            usuarioService.obtenerDocente("fantasma")
        }

        assertEquals("Usuario no encontrado: fantasma", exception.message)
    }

    @Test
    fun `obtenerOCrearAlumno returns existing user without saving`() {
        val existente = Usuario(id = 3L, username = "juan")
        `when`(usuarioRepository.findByUsername("juan")).thenReturn(existente)

        val result = usuarioService.obtenerOCrearAlumno("juan")

        assertEquals(existente, result)
        verify(usuarioRepository, never()).save(any())
    }

    @Test
    fun `obtenerOCrearAlumno creates and saves new user when user does not exist`() {
        `when`(usuarioRepository.findByUsername("nuevo")).thenReturn(null)
        val nuevoGuardado = Usuario(id = 4L, username = "nuevo")
        `when`(usuarioRepository.save(any(Usuario::class.java))).thenReturn(nuevoGuardado)

        val result = usuarioService.obtenerOCrearAlumno("nuevo")

        assertNotNull(result)
        assertEquals("nuevo", result.username)
        verify(usuarioRepository).save(any(Usuario::class.java))
    }

    @Test
    fun `obtenerUsuario returns user when found`() {
        val usuario = Usuario(id = 5L, username = "usuario1", nombreCompleto = "Pérez, Juan")
        `when`(usuarioRepository.findByUsername("usuario1")).thenReturn(usuario)

        val result = usuarioService.obtenerUsuario("usuario1")

        assertEquals("usuario1", result.username)
        assertEquals("Pérez, Juan", result.nombreCompleto)
    }

    @Test
    fun `obtenerUsuario throws UsuarioNotFoundException when not found`() {
        `when`(usuarioRepository.findByUsername("inexistente")).thenReturn(null)

        assertThrows<UsuarioNotFoundException> {
            usuarioService.obtenerUsuario("inexistente")
        }
    }

    @Test
    fun `actualizarNombreCompleto updates and persists user with formatted and sanitized Apellidos, Nombres`() {
        val usuario = Usuario(id = 6L, username = "usuario2", nombreCompleto = "Nombre Viejo")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)
        `when`(usuarioRepository.save(usuario)).thenReturn(usuario)

        val result = usuarioService.actualizarNombreCompleto("usuario2", "  Juan   Manuel  ", "  García   Lorca  ")

        assertEquals("García Lorca, Juan Manuel", result.nombreCompleto)
        verify(usuarioRepository).save(usuario)
    }

    @Test
    fun `actualizarNombreCompleto accepts compound names with accents, hyphens and apostrophes`() {
        val usuario = Usuario(id = 7L, username = "usuario3", nombreCompleto = null)
        `when`(usuarioRepository.findByUsername("usuario3")).thenReturn(usuario)
        `when`(usuarioRepository.save(usuario)).thenReturn(usuario)

        val result = usuarioService.actualizarNombreCompleto("usuario3", "Martín", "D'Angelo-García")

        assertEquals("D'Angelo-García, Martín", result.nombreCompleto)
        verify(usuarioRepository).save(usuario)
    }

    @Test
    fun `actualizarNombreCompleto accepts enye en nombre y apellido`() {
        val usuario = Usuario(id = 8L, username = "usuario4", nombreCompleto = null)
        `when`(usuarioRepository.findByUsername("usuario4")).thenReturn(usuario)
        `when`(usuarioRepository.save(usuario)).thenReturn(usuario)

        val result = usuarioService.actualizarNombreCompleto("usuario4", "Iñaki", "Ñúñez Peña")

        assertEquals("Ñúñez Peña, Iñaki", result.nombreCompleto)
        verify(usuarioRepository).save(usuario)
    }

    @Test
    fun `actualizarNombreCompleto throws NombreUsuarioInvalidoException when combined name exceeds 35 chars`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)

        assertThrows<NombreUsuarioInvalidoException> {
            usuarioService.actualizarNombreCompleto("usuario2", "Nombre Extremadamente Largo", "Apellido Extremadamente Largo")
        }
    }

    @Test
    fun `actualizarNombreCompleto throws NombreUsuarioInvalidoException when nombre is blank`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)

        assertThrows<NombreUsuarioInvalidoException> {
            usuarioService.actualizarNombreCompleto("usuario2", "   ", "Pérez")
        }
    }

    @Test
    fun `actualizarNombreCompleto throws NombreUsuarioInvalidoException when apellido is blank`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)

        assertThrows<NombreUsuarioInvalidoException> {
            usuarioService.actualizarNombreCompleto("usuario2", "Juan", "   ")
        }
    }

    @Test
    fun `actualizarNombreCompleto throws NombreUsuarioInvalidoException when contains invalid chars like numbers or symbols`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)

        assertThrows<NombreUsuarioInvalidoException> {
            usuarioService.actualizarNombreCompleto("usuario2", "Juan123", "Pérez")
        }

        assertThrows<NombreUsuarioInvalidoException> {
            usuarioService.actualizarNombreCompleto("usuario2", "Juan", "Pérez@")
        }
    }

    @Test
    fun `actualizarEmail updates and persists user when email is valid`() {
        val usuario = Usuario(id = 6L, username = "usuario2", email = "viejo@unq.com")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)
        `when`(usuarioRepository.findByEmailIgnoreCase("nuevo@unq.com.ar")).thenReturn(null)
        `when`(usuarioRepository.findByDni("12345678")).thenReturn(null)
        `when`(usuarioRepository.save(usuario)).thenReturn(usuario)

        val result = usuarioService.actualizarDatos(
            username = "usuario2",
            nombre = "Juan",
            apellido = "Pérez",
            email = "nuevo@unq.com.ar",
            dni = "12345678",
        )

        assertEquals("nuevo@unq.com.ar", result.email)
        verify(usuarioRepository).save(usuario)
    }

    @Test
    fun `actualizarEmail throws EmailInvalidoException when email is blank`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)

        assertThrows<EmailInvalidoException> {
            usuarioService.actualizarDatos(
                username = "usuario2",
                nombre = "Juan",
                apellido = "Pérez",
                email = "   ",
                dni = "12345678",
            )
        }
    }

    @Test
    fun `actualizarEmail throws EmailInvalidoException when format is invalid`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)

        assertThrows<EmailInvalidoException> {
            usuarioService.actualizarDatos(
                username = "usuario2",
                nombre = "Juan",
                apellido = "Pérez",
                email = "correo-invalido",
                dni = "12345678",
            )
        }

        assertThrows<EmailInvalidoException> {
            usuarioService.actualizarDatos(
                username = "usuario2",
                nombre = "Juan",
                apellido = "Pérez",
                email = "usuario@dominio.org",
                dni = "12345678",
            )
        }
    }

    @Test
    fun `actualizarEmail throws EmailDuplicadoException when email is taken by another user`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        val otroUsuario = Usuario(id = 9L, username = "otro")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)
        `when`(usuarioRepository.findByEmailIgnoreCase("repetido@unq.com")).thenReturn(otroUsuario)

        assertThrows<EmailDuplicadoException> {
            usuarioService.actualizarDatos(
                username = "usuario2",
                nombre = "Juan",
                apellido = "Pérez",
                email = "repetido@unq.com",
                dni = "12345678",
            )
        }
    }

    @Test
    fun `actualizarDni updates and persists user when dni is valid`() {
        val usuario = Usuario(id = 6L, username = "usuario2", dni = "11111111")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)
        `when`(usuarioRepository.findByEmailIgnoreCase("test@unq.com")).thenReturn(null)
        `when`(usuarioRepository.findByDni("40123456")).thenReturn(null)
        `when`(usuarioRepository.save(usuario)).thenReturn(usuario)

        val result = usuarioService.actualizarDatos(
            username = "usuario2",
            nombre = "Juan",
            apellido = "Pérez",
            email = "test@unq.com",
            dni = "40123456",
        )

        assertEquals("40123456", result.dni)
        verify(usuarioRepository).save(usuario)
    }

    @Test
    fun `actualizarDni throws DniInvalidoException when dni is blank`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)

        assertThrows<DniInvalidoException> {
            usuarioService.actualizarDatos(
                username = "usuario2",
                nombre = "Juan",
                apellido = "Pérez",
                email = "test@unq.com",
                dni = "   ",
            )
        }
    }

    @Test
    fun `actualizarDni throws DniInvalidoException when length is invalid or not numeric`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)

        assertThrows<DniInvalidoException> {
            usuarioService.actualizarDatos(
                username = "usuario2",
                nombre = "Juan",
                apellido = "Pérez",
                email = "test@unq.com",
                dni = "123456",
            )
        }

        assertThrows<DniInvalidoException> {
            usuarioService.actualizarDatos(
                username = "usuario2",
                nombre = "Juan",
                apellido = "Pérez",
                email = "test@unq.com",
                dni = "123456789",
            )
        }

        assertThrows<DniInvalidoException> {
            usuarioService.actualizarDatos(
                username = "usuario2",
                nombre = "Juan",
                apellido = "Pérez",
                email = "test@unq.com",
                dni = "1234ABCD",
            )
        }
    }

    @Test
    fun `actualizarDni throws DniDuplicadoException when dni is taken by another user`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        val otroUsuario = Usuario(id = 9L, username = "otro")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)
        `when`(usuarioRepository.findByEmailIgnoreCase("test@unq.com")).thenReturn(null)
        `when`(usuarioRepository.findByDni("40123456")).thenReturn(otroUsuario)

        assertThrows<DniDuplicadoException> {
            usuarioService.actualizarDatos(
                username = "usuario2",
                nombre = "Juan",
                apellido = "Pérez",
                email = "test@unq.com",
                dni = "40123456",
            )
        }
    }
}
