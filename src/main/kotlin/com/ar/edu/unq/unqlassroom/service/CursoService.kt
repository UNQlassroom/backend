package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.dto.curso.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.dto.curso.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.dto.curso.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.dto.curso.AlumnosDeUnCursoResponseDTO

interface CursoService {

    fun crearCurso(dto: CursoRequestDTO, ownerUsername: String): CursoResponseDTO

    fun agregarAlumnos(cursoId: Long, dto: AgregarAlumnosRequestDTO, solicitanteUsername: String): AlumnosDeUnCursoResponseDTO

    fun sincronizarAlumnos(cursoId: Long, solicitanteUsername: String): AlumnosDeUnCursoResponseDTO

    fun obtenerCursos(username: String, esDocente: Boolean): List<CursoResponseDTO>

    fun obtenerCurso(id: Long, solicitanteUsername: String): CursoResponseDTO

    fun obtenerAlumnos(cursoId: Long, solicitanteUsername: String): AlumnosDeUnCursoResponseDTO
}
