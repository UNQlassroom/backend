package com.ar.edu.unq.unqlassroom.model

import jakarta.persistence.*

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
    var githubRole: String = "push",

    @Column(nullable = false)
    var githubState: String = "pending",

    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "repositorio_id")
    var repositorio: Repositorio? = null,
)
