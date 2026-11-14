package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.dto.sse.SseEventDTO
import com.ar.edu.unq.unqlassroom.dto.sse.SseEventType
import com.ar.edu.unq.unqlassroom.model.Asignacion
import com.ar.edu.unq.unqlassroom.model.GrupoAsignacion
import com.ar.edu.unq.unqlassroom.repository.GrupoAsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.SseNotificationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Service
class SseNotificationServiceImpl(
    private val grupoAsignacionRepository: GrupoAsignacionRepository,
    private val inscripcionRepository: InscripcionRepository,
    private val usuarioRepository: UsuarioRepository,
) : SseNotificationService {

    private val logger = LoggerFactory.getLogger(javaClass)

    // Key: username en minúsculas, Value: lista de conexiones activas (pestañas)
    private val emittersPorUsuario = ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>>()

    override fun suscribir(username: String): SseEmitter {
        val clave = username.trim().lowercase()
        val emitter = SseEmitter(0L) // Sin timeout en el backend
        val lista = emittersPorUsuario.computeIfAbsent(clave) { CopyOnWriteArrayList() }
        lista.add(emitter)

        val remover = {
            lista.remove(emitter)
            if (lista.isEmpty()) {
                emittersPorUsuario.remove(clave, lista)
            }
            logger.debug("Conexión SSE cerrada para usuario: {}", clave)
        }

        emitter.onCompletion { remover() }
        emitter.onTimeout { remover() }
        emitter.onError { remover() }

        // Enviar evento de conexión inicial
        try {
            emitter.send(
                SseEmitter.event()
                    .name("connected")
                    .data("ok")
            )
        } catch (e: Exception) {
            logger.warn("No se pudo enviar evento inicial SSE para {}: {}", clave, e.message)
            remover()
        }

        logger.info("Nueva conexión SSE establecida para usuario: {} (total conexiones usuario: {})", clave, lista.size)
        return emitter
    }

    @Scheduled(fixedRateString = "\${sse.heartbeat-interval-ms:25000}")
    fun enviarHeartbeat() {
        if (emittersPorUsuario.isEmpty()) return

        for ((usuario, lista) in emittersPorUsuario) {
            for (emitter in lista) {
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("ping")
                            .data("keep-alive")
                    )
                } catch (e: Exception) {
                    logger.debug("Error enviando ping SSE a {}: {}", usuario, e.message)
                    lista.remove(emitter)
                }
            }
            if (lista.isEmpty()) {
                emittersPorUsuario.remove(usuario, lista)
            }
        }
    }

    override fun notificarEventoDeRepositorio(repoNombre: String, tipo: SseEventType) {
        val grupo = try {
            grupoAsignacionRepository.findByRepositorioNombreIgnoreCase(repoNombre)
        } catch (e: Exception) {
            logger.warn("Error buscando grupo para repo {}: {}", repoNombre, e.message)
            null
        }

        if (grupo == null) {
            logger.debug("No se encontró grupo asociado al repo {} para notificar {}", repoNombre, tipo)
            return
        }

        val asignacion = grupo.asignacion
        val curso = asignacion?.curso

        val evento = SseEventDTO(
            type = tipo,
            cursoId = curso?.id,
            asignacionId = asignacion?.id,
            grupoId = grupo.id,
            repoNombre = repoNombre,
        )

        val destinatarios = mutableSetOf<String>()
        curso?.owner?.username?.let { destinatarios.add(it) }
        grupo.integrantes.forEach { destinatarios.add(it.username) }

        enviarAUsuarios(destinatarios, evento)
    }

    override fun notificarAsignacionActualizada(asignacion: Asignacion, grupoEspecifico: GrupoAsignacion?) {
        val curso = asignacion.curso
        val evento = SseEventDTO(
            type = SseEventType.ASIGNACION_ACTUALIZADA,
            cursoId = curso?.id,
            asignacionId = asignacion.id,
            grupoId = grupoEspecifico?.id,
        )

        val destinatarios = mutableSetOf<String>()
        curso?.owner?.username?.let { destinatarios.add(it) }

        if (grupoEspecifico != null) {
            grupoEspecifico.integrantes.forEach { destinatarios.add(it.username) }
        } else {
            asignacion.grupos.forEach { g ->
                g.integrantes.forEach { destinatarios.add(it.username) }
            }
        }

        enviarAUsuarios(destinatarios, evento)
    }

    override fun notificarMiembroCursoActualizado(
        cursoId: Long,
        docenteUsername: String?,
        alumnosUsernames: Collection<String>
    ) {
        val evento = SseEventDTO(
            type = SseEventType.MIEMBRO_ACTIVADO,
            cursoId = cursoId,
        )

        val destinatarios = mutableSetOf<String>()
        docenteUsername?.let { destinatarios.add(it) }
        destinatarios.addAll(alumnosUsernames)

        enviarAUsuarios(destinatarios, evento)
    }

    override fun notificarMiembroActivadoGlobal(alumnoUsername: String) {
        val inscripciones = try {
            inscripcionRepository.findByUsuarioUsernameIgnoreCase(alumnoUsername)
        } catch (e: Exception) {
            logger.warn("Error buscando inscripciones de alumno {}: {}", alumnoUsername, e.message)
            emptyList()
        }

        for (inscripcion in inscripciones) {
            val curso = inscripcion.curso
            val evento = SseEventDTO(
                type = SseEventType.MIEMBRO_ACTIVADO,
                cursoId = curso.id,
            )

            val destinatarios = mutableSetOf<String>()
            curso.owner?.username?.let { destinatarios.add(it) }
            destinatarios.add(alumnoUsername)

            enviarAUsuarios(destinatarios, evento)
        }
    }

    override fun notificarTemplatesActualizados(repoNombre: String) {
        val evento = SseEventDTO(
            type = SseEventType.TEMPLATES_ACTUALIZADOS,
            repoNombre = repoNombre,
        )

        val docentes = try {
            usuarioRepository.findByEsDocenteTrue().map { it.username }
        } catch (e: Exception) {
            logger.warn("Error buscando docentes para notificar templates: {}", e.message)
            emptyList()
        }

        enviarAUsuarios(docentes.toSet(), evento)
    }

    private fun enviarAUsuarios(usernames: Set<String>, evento: SseEventDTO) {
        for (username in usernames) {
            val clave = username.trim().lowercase()
            val lista = emittersPorUsuario[clave] ?: continue

            for (emitter in lista) {
                try {
                    emitter.send(
                        SseEmitter.event()
                            .data(evento)
                    )
                } catch (e: Exception) {
                    logger.debug("Error enviando SSE a {}: {}", clave, e.message)
                    lista.remove(emitter)
                }
            }

            if (lista.isEmpty()) {
                emittersPorUsuario.remove(clave, lista)
            }
        }
    }
}

