package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.exception.*
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class UsuarioServiceImpl(
    private val usuarioRepository: UsuarioRepository
) : UsuarioService {

    @Transactional(readOnly = true)
    override fun obtenerDocente(username: String): Usuario {
        val usuario = obtenerUsuario(username)

        if (!usuario.esDocente) {
            throw UsuarioNoEsDocenteException(username)
        }

        return usuario
    }

    override fun obtenerOCrearAlumno(username: String): Usuario {
        return usuarioRepository.findByUsername(username)
            ?: usuarioRepository.save(Usuario(username = username, esDocente = false))
    }

    @Transactional(readOnly = true)
    override fun obtenerUsuario(username: String): Usuario {
        return usuarioRepository.findByUsername(username)
            ?: throw UsuarioNotFoundException("Usuario no encontrado: $username")
    }

    override fun actualizarNombreCompleto(username: String, nombre: String, apellido: String): Usuario {
        val usuario = obtenerUsuario(username)
        usuario.actualizarNombreCompleto(nombre = nombre, apellido = apellido)
        return usuarioRepository.save(usuario)
    }

    override fun actualizarDatos(
        username: String,
        nombre: String,
        apellido: String,
        email: String,
        dni: String,
    ): Usuario {
        val usuario = obtenerUsuario(username)

        val emailTrimmed = email.trim()
        val dniTrimmed = dni.trim()

        val usuarioConEmail = usuarioRepository.findByEmailIgnoreCase(emailTrimmed)
        if (usuarioConEmail != null && usuarioConEmail.id != usuario.id) {
            throw EmailDuplicadoException()
        }

        val usuarioConDni = usuarioRepository.findByDni(dniTrimmed)
        if (usuarioConDni != null && usuarioConDni.id != usuario.id) {
            throw DniDuplicadoException()
        }

        usuario.actualizarDatos(
            nombre = nombre,
            apellido = apellido,
            email = emailTrimmed,
            dni = dniTrimmed,
        )

        return usuarioRepository.save(usuario)
    }
}
