package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.dto.curso.response.ArchivoExportadoDTO
import com.ar.edu.unq.unqlassroom.model.Curso

interface CursoExportService {
    fun exportarPlanillaCurso(curso: Curso): ArchivoExportadoDTO
}
