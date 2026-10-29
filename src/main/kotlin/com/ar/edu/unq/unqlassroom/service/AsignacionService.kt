package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.dto.asignacion.response.CorreccionGrupoResponseDTO
import com.ar.edu.unq.unqlassroom.model.Asignacion
import com.ar.edu.unq.unqlassroom.model.MetricaCI
import com.ar.edu.unq.unqlassroom.model.ReporteLinter
import com.ar.edu.unq.unqlassroom.model.Repositorio
import java.time.LocalDateTime

interface AsignacionService {
    fun crearAsignacion(cursoId: Long, asignacion: Asignacion, solicitanteUsername: String): Asignacion
    fun modificarAsignacion(cursoId: Long, asignacionId: Long, descripcion: String?, fechaLimite: LocalDateTime?, solicitanteUsername: String): Asignacion
    fun obtenerAsignaciones(cursoId: Long, solicitanteUsername: String): List<Asignacion>
    fun obtenerAsignacion(cursoId: Long, asignacionId: Long, solicitanteUsername: String): Asignacion
    fun marcarAsignacionComoEntregada(cursoId: Long, asignacionId: Long, solicitanteUsername: String, grupoId: Long? = null): Asignacion
    fun calificarAsignacion(cursoId: Long, asignacionId: Long, grupoId: Long, calificacion: Int, observaciones: String?, solicitanteUsername: String): Asignacion
    fun obtenerCorrecciones(cursoId: Long, asignacionId: Long, solicitanteUsername: String): List<CorreccionGrupoResponseDTO>
    fun obtenerDiagnosticoCI(cursoId: Long, asignacionId: Long, solicitanteUsername: String, grupoId: Long? = null): Repositorio
    fun obtenerMetricasCI(cursoId: Long, asignacionId: Long, solicitanteUsername: String, grupoId: Long? = null): Pair<Repositorio, MetricaCI?>
    fun obtenerReporteLinter(cursoId: Long, asignacionId: Long, solicitanteUsername: String, grupoId: Long? = null): Pair<Repositorio, ReporteLinter?>
    fun eliminarAsignacion(cursoId: Long, asignacionId: Long, solicitanteUsername: String)
}
