package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.exception.UsuarioNoEsDocenteException
import com.ar.edu.unq.unqlassroom.exception.UsuarioNotFoundException
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
        val usuario = usuarioRepository.findByUsername(username)
            ?: throw UsuarioNotFoundException("Usuario no encontrado: $username")

        if (!usuario.esDocente) {
            throw UsuarioNoEsDocenteException(username)
        }

        return usuario
    }

    override fun obtenerOCrearAlumno(username: String): Usuario {
        return usuarioRepository.findByUsername(username)
            ?: usuarioRepository.save(Usuario(username = username, esDocente = false))
    }
}
