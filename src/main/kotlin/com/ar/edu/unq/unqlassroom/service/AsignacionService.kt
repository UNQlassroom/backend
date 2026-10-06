package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.dto.asignacion.response.CorreccionGrupoResponseDTO
import com.ar.edu.unq.unqlassroom.model.Asignacion
import com.ar.edu.unq.unqlassroom.model.GrupoAsignacion

interface AsignacionService {
    fun crearAsignacion(cursoId: Long, asignacion: Asignacion, solicitanteUsername: String): Asignacion
    fun obtenerAsignaciones(cursoId: Long, solicitanteUsername: String): List<Asignacion>
    fun obtenerAsignacion(cursoId: Long, asignacionId: Long, solicitanteUsername: String): Asignacion
    fun marcarAsignacionComoEntregada(cursoId: Long, asignacionId: Long, solicitanteUsername: String, grupoId: Long? = null): Asignacion
    fun calificarAsignacion(cursoId: Long, asignacionId: Long, grupoId: Long, calificacion: Int, observaciones: String?, solicitanteUsername: String): Asignacion
    fun obtenerCorrecciones(cursoId: Long, asignacionId: Long, solicitanteUsername: String): List<CorreccionGrupoResponseDTO>
}
