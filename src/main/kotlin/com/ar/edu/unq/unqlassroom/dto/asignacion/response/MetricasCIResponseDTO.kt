package com.ar.edu.unq.unqlassroom.dto.asignacion.response

import com.ar.edu.unq.unqlassroom.model.MetricaCI
import java.time.LocalDateTime

data class MetricasCIResponseDTO(
    val id: Long? = null,
    val repoNombre: String,
    val commitSha: String? = null,
    val coberturaLineas: Double = 0.0,
    val lineasCubiertas: Int = 0,
    val lineasPerdidas: Int = 0,
    val umbralCobertura: Double = 80.0,
    val cumpleUmbralCobertura: Boolean = false,
    val testsEjecutados: Int = 0,
    val testsPasados: Int = 0,
    val testsFallados: Int = 0,
    val testsSalteados: Int = 0,
    val autorUsername: String? = null,
    val runId: Long? = null,
    val fechaActualizacion: LocalDateTime? = null,
) {
    companion object {
        fun desdeModelo(modelo: MetricaCI): MetricasCIResponseDTO {
            return MetricasCIResponseDTO(
                id = modelo.id,
                repoNombre = modelo.repositorio.nombre,
                commitSha = modelo.commitSha,
                coberturaLineas = modelo.coberturaLineas,
                lineasCubiertas = modelo.lineasCubiertas,
                lineasPerdidas = modelo.lineasPerdidas,
                umbralCobertura = modelo.umbralCobertura,
                cumpleUmbralCobertura = modelo.cumpleUmbralCobertura,
                testsEjecutados = modelo.testsEjecutados,
                testsPasados = modelo.testsPasados,
                testsFallados = modelo.testsFallados,
                testsSalteados = modelo.testsSalteados,
                autorUsername = modelo.autorUsername ?: modelo.alumno?.username,
                runId = modelo.runId,
                fechaActualizacion = modelo.fechaActualizacion,
            )
        }

        fun sinMetricas(repoNombre: String, umbralCobertura: Double = 80.0): MetricasCIResponseDTO {
            return MetricasCIResponseDTO(
                repoNombre = repoNombre,
                umbralCobertura = umbralCobertura,
            )
        }
    }
}

