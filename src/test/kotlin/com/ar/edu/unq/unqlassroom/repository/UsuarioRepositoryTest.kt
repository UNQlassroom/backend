package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.Usuario
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class UsuarioRepositoryTest {

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Test
    fun `findByUsername returns user when exists`() {
        val user = Usuario(
            username = "docente_juan",
            nombreCompleto = "Juan Perez",
            email = "juan@unq.com.ar",
            dni = "12345678",
            esDocente = true
        )
        usuarioRepository.save(user)

        val found = usuarioRepository.findByUsername("docente_juan")
        assertNotNull(found)
        assertEquals("docente_juan", found?.username)
        assertEquals("Juan Perez", found?.nombreCompleto)
        assertEquals("12345678", found?.dni)
        assertTrue(found?.esDocente == true)
    }

    @Test
    fun `findByUsername returns null when user does not exist`() {
        val found = usuarioRepository.findByUsername("inexistente")
        assertNull(found)
    }

    @Test
    fun `existsByUsername returns true when user exists and false otherwise`() {
        val user = Usuario(username = "alumno_maria", esDocente = false)
        usuarioRepository.save(user)

        assertTrue(usuarioRepository.existsByUsername("alumno_maria"))
        assertFalse(usuarioRepository.existsByUsername("alumno_desconocido"))
    }

    @Test
    fun `findByEmailIgnoreCase returns user case-insensitively`() {
        val user = Usuario(
            username = "alumno_carlos",
            email = "carlos@correo.com",
            dni = "40123456",
            esDocente = false
        )
        usuarioRepository.save(user)

        val found = usuarioRepository.findByEmailIgnoreCase("CARLOS@CORREO.COM")
        assertNotNull(found)
        assertEquals("alumno_carlos", found?.username)
        assertEquals("carlos@correo.com", found?.email)
    }

    @Test
    fun `findByDni returns user when exists`() {
        val user = Usuario(
            username = "alumno_lucas",
            email = "lucas@correo.com.ar",
            dni = "38999888",
            esDocente = false
        )
        usuarioRepository.save(user)

        val found = usuarioRepository.findByDni("38999888")
        assertNotNull(found)
        assertEquals("alumno_lucas", found?.username)
        assertEquals("38999888", found?.dni)
    }
}
