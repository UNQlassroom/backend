package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.dto.curso.response.ArchivoExportadoDTO
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Inscripcion
import com.ar.edu.unq.unqlassroom.model.Usuario
import java.time.LocalDate

interface CursoService {

    fun crearCurso(curso: Curso, creadorUsername: String): Curso

    fun agregarAlumnos(cursoId: Long, usernames: List<String>, solicitanteUsername: String): List<Inscripcion>

    fun darDeBajaAlumno(cursoId: Long, alumnoUsername: String, solicitanteUsername: String)

    fun agregarDocentes(cursoId: Long, usernames: List<String>, solicitanteUsername: String): List<Usuario>

    fun obtenerDocentes(cursoId: Long, solicitanteUsername: String): List<Usuario>

    fun darDeBajaDocente(cursoId: Long, docenteUsername: String, solicitanteUsername: String)

    fun obtenerCursos(username: String, esDocente: Boolean, archivados: Boolean = false): List<Curso>

    fun obtenerCurso(id: Long, solicitanteUsername: String): Curso

    fun obtenerAlumnos(cursoId: Long, solicitanteUsername: String): List<Inscripcion>

    fun archivarCurso(cursoId: Long, solicitanteUsername: String): Curso

    fun archivarCursosVencidosAutomaticamente(fechaActual: LocalDate = LocalDate.now())

    fun exportarCurso(id: Long, solicitanteUsername: String): ArchivoExportadoDTO
}
