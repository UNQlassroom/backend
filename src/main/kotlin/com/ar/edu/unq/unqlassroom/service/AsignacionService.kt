package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.dto.asignacion.AsignacionResponseDTO
import com.ar.edu.unq.unqlassroom.dto.asignacion.CalificarAsignacionRequestDTO
import com.ar.edu.unq.unqlassroom.dto.asignacion.CrearAsignacionRequestDTO
import com.ar.edu.unq.unqlassroom.dto.asignacion.CrearTemplateRepoRequestDTO
import com.ar.edu.unq.unqlassroom.dto.asignacion.CorreccionGrupoResponseDTO
import com.ar.edu.unq.unqlassroom.dto.asignacion.TemplateRepoResponseDTO

interface AsignacionService {
    fun crearAsignacion(cursoId: Long, dto: CrearAsignacionRequestDTO, solicitanteUsername: String): AsignacionResponseDTO
    fun obtenerAsignaciones(cursoId: Long, solicitanteUsername: String): List<AsignacionResponseDTO>
    fun obtenerAsignacion(cursoId: Long, asignacionId: Long, solicitanteUsername: String): AsignacionResponseDTO
    fun crearTemplateRepository(dto: CrearTemplateRepoRequestDTO, solicitanteUsername: String): TemplateRepoResponseDTO
    fun listarTemplates(solicitanteUsername: String): List<TemplateRepoResponseDTO>
    fun marcarAsignacionComoEntregada(cursoId: Long, asignacionId: Long, solicitanteUsername: String, grupoId: Long? = null): AsignacionResponseDTO
    fun calificarAsignacion(cursoId: Long, asignacionId: Long, solicitanteUsername: String, dto: CalificarAsignacionRequestDTO): AsignacionResponseDTO
    fun obtenerCorrecciones(cursoId: Long, asignacionId: Long, solicitanteUsername: String): List<CorreccionGrupoResponseDTO>
}
