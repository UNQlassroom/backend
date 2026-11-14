package com.ar.edu.unq.unqlassroom.model

import jakarta.persistence.*

@Entity
@Table(name = "incidencias_linter")
class IncidenciaLinter(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "archivo", nullable = false, length = 500)
    var archivo: String,

    @Column(name = "linea", nullable = false)
    var linea: Int,

    @Column(name = "columna", nullable = true)
    var columna: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "severidad", nullable = false)
    var severidad: SeveridadLinter,

    @Column(name = "regla", nullable = false, length = 255)
    var regla: String,

    @Column(name = "mensaje", nullable = true, length = 1000)
    var mensaje: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporte_id", nullable = false)
    var reporte: ReporteLinter? = null,
)

