package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.Usuario

interface UsuarioService {
    fun obtenerDocente(username: String): Usuario
    fun obtenerOCrearAlumno(username: String): Usuario
    fun obtenerUsuario(username: String): Usuario
    fun actualizarNombreCompleto(username: String, nuevoNombre: String): Usuario
}
