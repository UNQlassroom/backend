package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.exception.ForbiddenException
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
        require(materia.isNotBlank()) { "El nombre de la materia no puede estar vacío" }
        require(anio >= 2000) { "El año debe ser mayor o igual a 2000" }
        require(semestre in 1..2) { "El semestre debe ser 1 o 2" }
        require(comision > 0) { "La comisión debe ser mayor a 0" }
    }

    fun esOwner(username: String?): Boolean =
        owner?.username != null && owner?.username == username

    fun asignarOwner(docente: Usuario) {
        if (!docente.esDocente) {
            throw ForbiddenException("El usuario ${docente.username} no tiene permisos de docente")
        }
        this.owner = docente
    }

    fun generarDescripcion(): String = "Curso de $materia - Año $anio - Semestre $semestre - Comisión $comision"
}
