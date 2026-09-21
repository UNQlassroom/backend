package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.errors.ForbiddenException
import com.ar.edu.unq.unqlassroom.errors.UsuarioNotFoundException
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.impl.UsuarioServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
    fun `obtenerDocente throws ForbiddenException when user exists but is not docente`() {
        val alumno = Usuario(id = 2L, username = "alumno", esDocente = false)
        `when`(usuarioRepository.findByUsername("alumno")).thenReturn(alumno)

        val exception = assertThrows<ForbiddenException> {
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
}
