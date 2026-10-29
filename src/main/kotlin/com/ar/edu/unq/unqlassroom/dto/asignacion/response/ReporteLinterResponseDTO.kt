package com.ar.edu.unq.unqlassroom.dto.asignacion.response

import com.ar.edu.unq.unqlassroom.model.IncidenciaLinter
import com.ar.edu.unq.unqlassroom.model.ReporteLinter
import com.ar.edu.unq.unqlassroom.model.SeveridadLinter
import java.time.LocalDateTime

data class IncidenciaLinterResponseDTO(
    val id: Long? = null,
    val archivo: String,
    val linea: Int,
    val columna: Int? = null,
    val severidad: SeveridadLinter,
    val regla: String,
    val mensaje: String? = null,
) {
    companion object {
        fun desdeModelo(modelo: IncidenciaLinter): IncidenciaLinterResponseDTO {
            return IncidenciaLinterResponseDTO(
                id = modelo.id,
                archivo = modelo.archivo,
                linea = modelo.linea,
                columna = modelo.columna,
                severidad = modelo.severidad,
                regla = modelo.regla,
                mensaje = modelo.mensaje,
            )
        }
    }
}

data class ReporteLinterResponseDTO(
    val id: Long? = null,
    val repoNombre: String,
    val commitSha: String? = null,
    val herramienta: String? = null,
    val totalIncidencias: Int = 0,
    val cantidadErrores: Int = 0,
    val cantidadAdvertencias: Int = 0,
    val incidencias: List<IncidenciaLinterResponseDTO> = emptyList(),
    val autorUsername: String? = null,
    val runId: Long? = null,
    val fechaActualizacion: LocalDateTime? = null,
) {
    companion object {
        fun desdeModelo(modelo: ReporteLinter): ReporteLinterResponseDTO {
            return ReporteLinterResponseDTO(
                id = modelo.id,
                repoNombre = modelo.repositorio.nombre,
                commitSha = modelo.commitSha,
                herramienta = modelo.herramienta,
                totalIncidencias = modelo.totalIncidencias,
                cantidadErrores = modelo.cantidadErrores,
                cantidadAdvertencias = modelo.cantidadAdvertencias,
                incidencias = modelo.incidencias.map { IncidenciaLinterResponseDTO.desdeModelo(it) },
                autorUsername = modelo.autorUsername ?: modelo.alumno?.username,
                runId = modelo.runId,
                fechaActualizacion = modelo.fechaActualizacion,
            )
        }

        fun sinReporte(repoNombre: String): ReporteLinterResponseDTO {
            return ReporteLinterResponseDTO(
                repoNombre = repoNombre,
            )
        }
    }
}

