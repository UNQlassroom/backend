package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnosDeUnCursoResponseDTO
import org.springframework.security.core.Authentication

interface CursoService {

    fun crearCurso(dto: CursoRequestDTO, authentication: Authentication): CursoResponseDTO

    fun agregarAlumnos(cursoId: Long, dto: AgregarAlumnosRequestDTO): AlumnosDeUnCursoResponseDTO

    fun obtenerCursos(): List<CursoResponseDTO>

    fun obtenerAlumnos(cursoId: Long): AlumnosDeUnCursoResponseDTO
}