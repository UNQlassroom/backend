package com.ar.edu.unq.unqlassroom.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "metricas_ci",
    uniqueConstraints = [UniqueConstraint(columnNames = ["repositorio_id", "commit_sha"])]
)
class MetricaCI(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "commit_sha", nullable = false)
    var commitSha: String,

    @Column(name = "cobertura_lineas", nullable = false)
    var coberturaLineas: Double = 0.0,

    @Column(name = "lineas_cubiertas", nullable = false)
    var lineasCubiertas: Int = 0,

    @Column(name = "lineas_perdidas", nullable = false)
    var lineasPerdidas: Int = 0,

    @Column(name = "umbral_cobertura", nullable = false)
    var umbralCobertura: Double = 80.0,

    @Column(name = "cumple_umbral_cobertura", nullable = false)
    var cumpleUmbralCobertura: Boolean = false,

    @Column(name = "tests_ejecutados", nullable = false)
    var testsEjecutados: Int = 0,

    @Column(name = "tests_pasados", nullable = false)
    var testsPasados: Int = 0,

    @Column(name = "tests_fallados", nullable = false)
    var testsFallados: Int = 0,

    @Column(name = "tests_salteados", nullable = false)
    var testsSalteados: Int = 0,

    @Column(name = "autor_username", nullable = true)
    var autorUsername: String? = null,

    @Column(name = "run_id", nullable = true)
    var runId: Long? = null,

    @Column(name = "fecha_creacion", nullable = false)
    var fechaCreacion: LocalDateTime = LocalDateTime.now(),

    @Column(name = "fecha_actualizacion", nullable = false)
    var fechaActualizacion: LocalDateTime = LocalDateTime.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repositorio_id", nullable = false)
    var repositorio: Repositorio,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_asignacion_id", nullable = true)
    var grupoAsignacion: GrupoAsignacion? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignacion_id", nullable = true)
    var asignacion: Asignacion? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumno_id", nullable = true)
    var alumno: Usuario? = null,
) {
    fun actualizarDesdeReporte(
        coberturaLineas: Double,
        lineasCubiertas: Int,
        lineasPerdidas: Int,
        umbralCobertura: Double,
        testsEjecutados: Int,
        testsPasados: Int,
        testsFallados: Int,
        testsSalteados: Int,
        autorUsername: String? = this.autorUsername,
        runId: Long? = this.runId,
        grupoAsignacion: GrupoAsignacion? = this.grupoAsignacion,
        asignacion: Asignacion? = this.asignacion,
        alumno: Usuario? = this.alumno,
    ) {
        this.coberturaLineas = coberturaLineas
        this.lineasCubiertas = lineasCubiertas
        this.lineasPerdidas = lineasPerdidas
        this.umbralCobertura = umbralCobertura
        this.cumpleUmbralCobertura = coberturaLineas >= umbralCobertura
        this.testsEjecutados = testsEjecutados
        this.testsPasados = testsPasados
        this.testsFallados = testsFallados
        this.testsSalteados = testsSalteados
        if (!autorUsername.isNullOrBlank()) {
            this.autorUsername = autorUsername
        }
        if (runId != null && runId > 0L) {
            this.runId = runId
        }
        if (grupoAsignacion != null) {
            this.grupoAsignacion = grupoAsignacion
        }
        if (asignacion != null) {
            this.asignacion = asignacion
        }
        if (alumno != null) {
            this.alumno = alumno
        }
        this.fechaActualizacion = LocalDateTime.now()
    }
}

