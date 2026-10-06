package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Inscripcion

interface CursoService {

    fun crearCurso(curso: Curso, ownerUsername: String): Curso

    fun agregarAlumnos(cursoId: Long, usernames: List<String>, solicitanteUsername: String): List<Inscripcion>

    fun sincronizarAlumnos(cursoId: Long, solicitanteUsername: String): List<Inscripcion>

    fun obtenerCursos(username: String, esDocente: Boolean): List<Curso>

    fun obtenerCurso(id: Long, solicitanteUsername: String): Curso

    fun obtenerAlumnos(cursoId: Long, solicitanteUsername: String): List<Inscripcion>
}
