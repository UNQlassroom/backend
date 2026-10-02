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
            email = "juan@unq.edu.ar",
            esDocente = true
        )
        usuarioRepository.save(user)

        val found = usuarioRepository.findByUsername("docente_juan")
        assertNotNull(found)
        assertEquals("docente_juan", found?.username)
        assertEquals("Juan Perez", found?.nombreCompleto)
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
}
