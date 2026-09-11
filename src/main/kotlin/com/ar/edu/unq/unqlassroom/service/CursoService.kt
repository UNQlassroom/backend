package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.ObtenerAlumnosResponseDTO

interface CursoService {

    fun crearCurso(dto: CursoRequestDTO): CursoResponseDTO

    fun agregarAlumnos(cursoId: Long, dto: AgregarAlumnosRequestDTO): AgregarAlumnosResponseDTO

    fun obtenerCursos(): List<CursoResponseDTO>

    fun obtenerAlumnos(cursoId: Long): ObtenerAlumnosResponseDTO
}