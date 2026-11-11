package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.exception.NombreUsuarioInvalidoException
import com.ar.edu.unq.unqlassroom.exception.UsuarioNoEsDocenteException
import com.ar.edu.unq.unqlassroom.exception.UsuarioNotFoundException
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
        val usuario = Usuario(id = 5L, username = "usuario1", nombreCompleto = "Juan Pérez")
        `when`(usuarioRepository.findByUsername("usuario1")).thenReturn(usuario)

        val result = usuarioService.obtenerUsuario("usuario1")

        assertEquals("usuario1", result.username)
        assertEquals("Juan Pérez", result.nombreCompleto)
    }

    @Test
    fun `obtenerUsuario throws UsuarioNotFoundException when not found`() {
        `when`(usuarioRepository.findByUsername("inexistente")).thenReturn(null)

        assertThrows<UsuarioNotFoundException> {
            usuarioService.obtenerUsuario("inexistente")
        }
    }

    @Test
    fun `actualizarNombreCompleto updates and persists user with sanitized name`() {
        val usuario = Usuario(id = 6L, username = "usuario2", nombreCompleto = "Nombre Viejo")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)
        `when`(usuarioRepository.save(usuario)).thenReturn(usuario)

        val result = usuarioService.actualizarNombreCompleto("usuario2", "  María   de   los Ángeles  ")

        assertEquals("María de los Ángeles", result.nombreCompleto)
        verify(usuarioRepository).save(usuario)
    }

    @Test
    fun `actualizarNombreCompleto throws NombreUsuarioInvalidoException when name exceeds 35 chars`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)

        assertThrows<NombreUsuarioInvalidoException> {
            usuarioService.actualizarNombreCompleto("usuario2", "Nombre Extremadamente Largo Que Excede Los Treinta Y Cinco Caracteres")
        }
        verify(usuarioRepository, never()).save(any())
    }

    @Test
    fun `actualizarNombreCompleto throws NombreUsuarioInvalidoException when name contains invalid characters`() {
        val usuario = Usuario(id = 6L, username = "usuario2")
        `when`(usuarioRepository.findByUsername("usuario2")).thenReturn(usuario)

        assertThrows<NombreUsuarioInvalidoException> {
            usuarioService.actualizarNombreCompleto("usuario2", "Juan 123 @#$%")
        }
        verify(usuarioRepository, never()).save(any())
    }
}
