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
    fun `obtenerDocente returns user when user exists and is docente`() {
        val docente = Usuario(id = 1L, username = "profe", esDocente = true)
        `when`(usuarioRepository.findByUsername("profe")).thenReturn(docente)

        val result = usuarioService.obtenerDocente("profe")

        assertNotNull(result)
        assertEquals("profe", result.username)
        assertTrue(result.esDocente)
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
    fun `obtenerDocente throws UsuarioNoEsDocenteException when user exists but is not docente`() {
        val alumno = Usuario(id = 2L, username = "alumno", esDocente = false)
        `when`(usuarioRepository.findByUsername("alumno")).thenReturn(alumno)

        val exception = assertThrows<UsuarioNoEsDocenteException> {
            usuarioService.obtenerDocente("alumno")
        }

        assertEquals("El usuario alumno no tiene permisos de docente", exception.message)
    }

    @Test
    fun `obtenerOCrearAlumno returns existing user without saving`() {
        val existente = Usuario(id = 3L, username = "juan", esDocente = false)
        `when`(usuarioRepository.findByUsername("juan")).thenReturn(existente)

        val result = usuarioService.obtenerOCrearAlumno("juan")

        assertEquals(existente, result)
        verify(usuarioRepository, never()).save(any())
    }

    @Test
    fun `obtenerOCrearAlumno creates and saves new user with esDocente false when user does not exist`() {
        `when`(usuarioRepository.findByUsername("nuevo")).thenReturn(null)
        val nuevoGuardado = Usuario(id = 4L, username = "nuevo", esDocente = false)
        `when`(usuarioRepository.save(any(Usuario::class.java))).thenReturn(nuevoGuardado)

        val result = usuarioService.obtenerOCrearAlumno("nuevo")

        assertNotNull(result)
        assertEquals("nuevo", result.username)
        assertFalse(result.esDocente)
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
        verify(usuarioRepository, never()).save(any())
    }

    @Test
    fun `actualizarNombreCompleto throws NombreUsuarioInvalidoException when name contains invalid characters`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)

        assertThrows<NombreUsuarioInvalidoException> {
            usuarioService.actualizarNombreCompleto("usuario2", "Juan 123", "Pérez")
        }
        verify(usuarioRepository, never()).save(any())
    }

    @Test
    fun `actualizarNombreCompleto throws NombreUsuarioInvalidoException when name or apellido is blank`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)

        assertThrows<NombreUsuarioInvalidoException> {
            usuarioService.actualizarNombreCompleto("usuario2", "   ", "Pérez")
        }
        verify(usuarioRepository, never()).save(any())
    }

    // --- Tests para actualizarDatos ---

    @Test
    fun `actualizarDatos updates email, dni and nombreCompleto when all provided and valid`() {
        val usuario = Usuario(id = 10L, username = "user1")
        `when`(usuarioRepository.findByUsername("user1")).thenReturn(usuario)
        `when`(usuarioRepository.findByEmailIgnoreCase("carlos@santana.com.ar")).thenReturn(null)
        `when`(usuarioRepository.findByDni("35123456")).thenReturn(null)
        `when`(usuarioRepository.save(usuario)).thenReturn(usuario)

        val result = usuarioService.actualizarDatos(
            username = "user1",
            nombre = "Carlos",
            apellido = "Santana",
            email = "carlos@santana.com.ar",
            dni = "35123456",
        )

        assertEquals("carlos@santana.com.ar", result.email)
        assertEquals("35123456", result.dni)
        assertEquals("Santana, Carlos", result.nombreCompleto)
        verify(usuarioRepository).save(usuario)
    }

    @Test
    fun `actualizarDatos succeeds when user keeps their current email and dni`() {
        val usuario = Usuario(
            id = 10L,
            username = "user1",
            email = "existente@correo.com",
            dni = "12345678",
            nombreCompleto = "Gómez, Ana",
        )
        `when`(usuarioRepository.findByUsername("user1")).thenReturn(usuario)
        `when`(usuarioRepository.findByEmailIgnoreCase("existente@correo.com")).thenReturn(usuario)
        `when`(usuarioRepository.findByDni("12345678")).thenReturn(usuario)
        `when`(usuarioRepository.save(usuario)).thenReturn(usuario)

        val result = usuarioService.actualizarDatos(
            username = "user1",
            nombre = "Ana",
            apellido = "Gómez",
            email = "existente@correo.com",
            dni = "12345678",
        )

        assertEquals("existente@correo.com", result.email)
        assertEquals("12345678", result.dni)
        verify(usuarioRepository).save(usuario)
    }

    @Test
    fun `actualizarDatos throws EmailDuplicadoException when email is registered in another account`() {
        val usuario1 = Usuario(id = 10L, username = "user1")
        val usuario2 = Usuario(id = 20L, username = "user2", email = "repetido@correo.com")
        `when`(usuarioRepository.findByUsername("user1")).thenReturn(usuario1)
        `when`(usuarioRepository.findByEmailIgnoreCase("repetido@correo.com")).thenReturn(usuario2)

        val ex = assertThrows<EmailDuplicadoException> {
            usuarioService.actualizarDatos(
                username = "user1",
                nombre = "Juan",
                apellido = "Pérez",
                email = "repetido@correo.com",
                dni = "12345678",
            )
        }
        assertEquals("EMAIL_DUPLICADO", ex.errorCode)
        verify(usuarioRepository, never()).save(any())
    }

    @Test
    fun `actualizarDatos throws DniDuplicadoException when dni is registered in another account`() {
        val usuario1 = Usuario(id = 10L, username = "user1")
        val usuario2 = Usuario(id = 20L, username = "user2", dni = "40123456")
        `when`(usuarioRepository.findByUsername("user1")).thenReturn(usuario1)
        `when`(usuarioRepository.findByEmailIgnoreCase("libre@correo.com")).thenReturn(null)
        `when`(usuarioRepository.findByDni("40123456")).thenReturn(usuario2)

        val ex = assertThrows<DniDuplicadoException> {
            usuarioService.actualizarDatos(
                username = "user1",
                nombre = "Juan",
                apellido = "Pérez",
                email = "libre@correo.com",
                dni = "40123456",
            )
        }
        assertEquals("DNI_DUPLICADO", ex.errorCode)
        verify(usuarioRepository, never()).save(any())
    }

    @Test
    fun `actualizarDatos throws EmailInvalidoException when email is not ending in com or com ar`() {
        val usuario = Usuario(id = 10L, username = "user1")
        `when`(usuarioRepository.findByUsername("user1")).thenReturn(usuario)

        val ex = assertThrows<EmailInvalidoException> {
            usuarioService.actualizarDatos(
                username = "user1",
                nombre = "Juan",
                apellido = "Pérez",
                email = "juan@unq.edu.ar",
                dni = "12345678",
            )
        }
        assertEquals("EMAIL_INVALIDO", ex.errorCode)
        verify(usuarioRepository, never()).save(any())
    }

    @Test
    fun `actualizarDatos throws DniInvalidoException when dni has invalid length`() {
        val usuario = Usuario(id = 10L, username = "user1")
        `when`(usuarioRepository.findByUsername("user1")).thenReturn(usuario)

        val ex = assertThrows<DniInvalidoException> {
            usuarioService.actualizarDatos(
                username = "user1",
                nombre = "Juan",
                apellido = "Pérez",
                email = "juan@correo.com",
                dni = "123456",
            )
        }
        assertEquals("DNI_INVALIDO", ex.errorCode)
        verify(usuarioRepository, never()).save(any())
    }
}
