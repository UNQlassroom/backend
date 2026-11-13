package com.ar.edu.unq.unqlassroom.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "inscripciones",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["curso_id", "usuario_id"])
    ]
)
class Inscripcion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curso_id", nullable = false)
    var curso: Curso,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    var usuario: Usuario,

    @Column(nullable = false)
    var githubRole: String = "member",

    @Column(nullable = false, columnDefinition = "boolean default true")
    var activa: Boolean = true,

    @Column(nullable = true)
    var fechaBaja: LocalDateTime? = null,
) {
    fun darDeBaja() {
        this.activa = false
        this.fechaBaja = LocalDateTime.now()
    }

    fun reactivar(nuevoGithubRole: String? = null) {
        this.activa = true
        this.fechaBaja = null
        if (nuevoGithubRole != null) {
            this.githubRole = nuevoGithubRole
        }
    }
}
