package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.AsignacionResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CalificarAsignacionRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CrearAsignacionRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CrearTemplateRepoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CorreccionGrupoResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.TemplateRepoResponseDTO

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
