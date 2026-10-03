package com.ar.edu.unq.unqlassroom.model

import jakarta.persistence.*

@Entity
@Table(name = "cursos")
class Curso (

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    var materia: String,

    @Column(nullable = false)
    var anio: Int,

    @Column(nullable = false)
    var semestre: Int,

    @Column(nullable = false)
    var comision: Int,

    @Column(nullable = true)
    var descripcion: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = true)
    var owner: Usuario? = null,

    @OneToMany(mappedBy = "curso", cascade = [CascadeType.ALL], orphanRemoval = true)
    var inscripciones: MutableList<Inscripcion> = mutableListOf(),

    @OneToMany(mappedBy = "curso", cascade = [CascadeType.ALL], orphanRemoval = true)
    var asignaciones: MutableList<Asignacion> = mutableListOf()
) {
    init {
        materia = materia.trim()
    }

    fun generarDescripcion(): String = "Curso de $materia - Año $anio - Semestre $semestre - Comisión $comision"
}
