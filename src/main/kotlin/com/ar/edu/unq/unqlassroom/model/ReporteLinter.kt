package com.ar.edu.unq.unqlassroom.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "reportes_linter",
    uniqueConstraints = [UniqueConstraint(columnNames = ["repositorio_id", "commit_sha"])]
)
class ReporteLinter(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "commit_sha", nullable = false)
    var commitSha: String,

    @Column(name = "herramienta", nullable = true, length = 100)
    var herramienta: String? = null,

    @Column(name = "cantidad_errores", nullable = false)
    var cantidadErrores: Int = 0,

    @Column(name = "cantidad_advertencias", nullable = false)
    var cantidadAdvertencias: Int = 0,

    @Column(name = "total_incidencias", nullable = false)
    var totalIncidencias: Int = 0,

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

    @OneToMany(
        mappedBy = "reporte",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.EAGER
    )
    var incidencias: MutableList<IncidenciaLinter> = mutableListOf(),
) {
    fun agregarIncidencia(incidencia: IncidenciaLinter) {
        incidencia.reporte = this
        this.incidencias.add(incidencia)
    }

    fun reemplazarIncidencias(nuevasIncidencias: List<IncidenciaLinter>) {
        this.incidencias.clear()
        nuevasIncidencias.forEach { agregarIncidencia(it) }
    }

    fun actualizarDesdeReporte(
        cantidadErrores: Int,
        cantidadAdvertencias: Int,
        nuevasIncidencias: List<IncidenciaLinter>,
        herramienta: String? = this.herramienta,
        autorUsername: String? = this.autorUsername,
        runId: Long? = this.runId,
        grupoAsignacion: GrupoAsignacion? = this.grupoAsignacion,
        asignacion: Asignacion? = this.asignacion,
        alumno: Usuario? = this.alumno,
    ) {
        this.cantidadErrores = cantidadErrores
        this.cantidadAdvertencias = cantidadAdvertencias
        this.totalIncidencias = cantidadErrores + cantidadAdvertencias
        if (!herramienta.isNullOrBlank()) {
            this.herramienta = herramienta
        }
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
        reemplazarIncidencias(nuevasIncidencias)
        this.fechaActualizacion = LocalDateTime.now()
    }
}

