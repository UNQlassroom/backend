package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.dto.sse.SseEventType
import com.ar.edu.unq.unqlassroom.model.Asignacion
import com.ar.edu.unq.unqlassroom.model.GrupoAsignacion
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

interface SseNotificationService {
    fun suscribir(username: String): SseEmitter
    fun notificarEventoDeRepositorio(repoNombre: String, tipo: SseEventType)
    fun notificarAsignacionActualizada(asignacion: Asignacion, grupoEspecifico: GrupoAsignacion? = null)
    fun notificarMiembroCursoActualizado(cursoId: Long, docenteUsername: String?, alumnosUsernames: Collection<String>)
    fun notificarMiembroActivadoGlobal(alumnoUsername: String)
    fun notificarTemplatesActualizados(repoNombre: String)
}

