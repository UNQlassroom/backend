package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnosDeUnCursoResponseDTO

interface CursoService {

    fun crearCurso(dto: CursoRequestDTO, ownerUsername: String): CursoResponseDTO

    fun agregarAlumnos(cursoId: Long, dto: AgregarAlumnosRequestDTO, solicitanteUsername: String): AlumnosDeUnCursoResponseDTO

    fun sincronizarAlumnos(cursoId: Long, solicitanteUsername: String): AlumnosDeUnCursoResponseDTO

    fun obtenerCursos(username: String, esDocente: Boolean): List<CursoResponseDTO>

    fun obtenerCurso(id: Long, solicitanteUsername: String): CursoResponseDTO

    fun obtenerAlumnos(cursoId: Long, solicitanteUsername: String): AlumnosDeUnCursoResponseDTO
}
