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
    var semestre: String,

    @Column(nullable = false)
    var comision: String,

    @Column(nullable = true)
    var descripcion: String? = null
)