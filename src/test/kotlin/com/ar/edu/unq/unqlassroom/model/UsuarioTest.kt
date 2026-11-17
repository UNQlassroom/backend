package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.exception.DniInvalidoException
import com.ar.edu.unq.unqlassroom.exception.EmailInvalidoException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class UsuarioTest {

    @Test
    fun `actualizarEmail sets lowercased and trimmed email when valid ending with dot com`() {
        val usuario = Usuario(username = "juan")
        usuario.actualizarEmail("  Juan.Perez@Gmail.COM  ")

        assertEquals("juan.perez@gmail.com", usuario.email)
    }

    @Test
    fun `actualizarEmail sets lowercased and trimmed email when valid ending with dot com dot ar`() {
        val usuario = Usuario(username = "juan")
        usuario.actualizarEmail("ALUMNO@UNQ.COM.AR")

        assertEquals("alumno@unq.com.ar", usuario.email)
    }

    @Test
    fun `actualizarEmail throws EmailInvalidoException when email is blank`() {
        val usuario = Usuario(username = "juan")
        val ex = assertThrows<EmailInvalidoException> {
            usuario.actualizarEmail("   ")
        }
        assertEquals("EMAIL_INVALIDO", ex.errorCode)
    }

    @Test
    fun `actualizarEmail throws EmailInvalidoException when email has no at symbol`() {
        val usuario = Usuario(username = "juan")
        assertThrows<EmailInvalidoException> {
            usuario.actualizarEmail("juan.gmail.com")
        }
    }

    @Test
    fun `actualizarEmail throws EmailInvalidoException when email does not end with dot com or dot com dot ar`() {
        val usuario = Usuario(username = "juan")
        assertThrows<EmailInvalidoException> {
            usuario.actualizarEmail("juan@unq.edu.ar")
        }
        assertThrows<EmailInvalidoException> {
            usuario.actualizarEmail("juan@empresa.org")
        }
        assertThrows<EmailInvalidoException> {
            usuario.actualizarEmail("juan@empresa.net")
        }
    }

    @Test
    fun `actualizarDni sets trimmed dni when valid with 7 digits`() {
        val usuario = Usuario(username = "juan")
        usuario.actualizarDni("  1234567  ")

        assertEquals("1234567", usuario.dni)
    }

    @Test
    fun `actualizarDni sets trimmed dni when valid with 8 digits`() {
        val usuario = Usuario(username = "juan")
        usuario.actualizarDni("87654321")

        assertEquals("87654321", usuario.dni)
    }

    @Test
    fun `actualizarDni throws DniInvalidoException when dni is blank`() {
        val usuario = Usuario(username = "juan")
        val ex = assertThrows<DniInvalidoException> {
            usuario.actualizarDni("   ")
        }
        assertEquals("DNI_INVALIDO", ex.errorCode)
    }

    @Test
    fun `actualizarDni throws DniInvalidoException when dni has less than 7 digits`() {
        val usuario = Usuario(username = "juan")
        assertThrows<DniInvalidoException> {
            usuario.actualizarDni("123456")
        }
    }

    @Test
    fun `actualizarDni throws DniInvalidoException when dni has more than 8 digits`() {
        val usuario = Usuario(username = "juan")
        assertThrows<DniInvalidoException> {
            usuario.actualizarDni("123456789")
        }
    }

    @Test
    fun `actualizarDni throws DniInvalidoException when dni contains non digits`() {
        val usuario = Usuario(username = "juan")
        assertThrows<DniInvalidoException> {
            usuario.actualizarDni("1234567A")
        }
        assertThrows<DniInvalidoException> {
            usuario.actualizarDni("12.345.678")
        }
    }

    @Test
    fun `actualizarDatos updates all fields correctly`() {
        val usuario = Usuario(username = "juan")
        usuario.actualizarDatos(
            nombre = "Juan",
            apellido = "Pérez",
            email = "juan@test.com",
            dni = "12345678",
        )

        assertEquals("juan@test.com", usuario.email)
        assertEquals("12345678", usuario.dni)
        assertEquals("Pérez, Juan", usuario.nombreCompleto)
    }
}
