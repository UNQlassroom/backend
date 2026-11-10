package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Inscripcion
import java.time.LocalDate

interface CursoService {

    fun crearCurso(curso: Curso, ownerUsername: String): Curso

    fun agregarAlumnos(cursoId: Long, usernames: List<String>, solicitanteUsername: String): List<Inscripcion>

    fun obtenerCursos(username: String, esDocente: Boolean, archivados: Boolean = false): List<Curso>

    fun obtenerCurso(id: Long, solicitanteUsername: String): Curso

    fun obtenerAlumnos(cursoId: Long, solicitanteUsername: String): List<Inscripcion>

    fun archivarCurso(cursoId: Long, solicitanteUsername: String): Curso

    fun archivarCursosVencidosAutomaticamente(fechaActual: LocalDate = LocalDate.now())
}
