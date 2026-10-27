package com.ar.edu.unq.unqlassroom.dto.asignacion.response

import com.ar.edu.unq.unqlassroom.model.Repositorio

data class DiagnosticoCIResponseDTO(
    val repoNombre: String,
    val estadoCI: String?,
    val etapaFallida: String?,
    val logError: String?,
    val runUrl: String?,
    val runId: Long? = null,
) {
    companion object {
        fun desdeModelo(repositorio: Repositorio): DiagnosticoCIResponseDTO {
            return DiagnosticoCIResponseDTO(
                repoNombre = repositorio.nombre,
                estadoCI = repositorio.estadoCI,
                etapaFallida = repositorio.ciEtapaFallida,
                logError = repositorio.ciLogError,
                runUrl = repositorio.ciRunUrl,
                runId = repositorio.ciRunId,
            )
        }
    }
}

