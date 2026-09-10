package com.ar.edu.unq.unqlassroom.model

import jakarta.persistence.*

@Entity
@Table(name = "repositorios")
class Repositorio(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    var nombre: String,

    @Column(nullable = false)
    var htmlUrl: String,

    @Column(nullable = true)
    var ultimoCommit: String? = null,

    @Column(nullable = true)
    var fechaUltimoCommit: String? = null,

    @Column(nullable = true)
    var estadoCI: String? = null,
)
