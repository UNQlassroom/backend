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

    @Column(nullable = false)
    var esTemplate: Boolean = false,

    @Column(nullable = true, length = 1000)
    var descripcion: String? = null,

    @Column(nullable = false)
    var eliminado: Boolean = false,
) {
    fun actualizarInfo(ultimoCommit: String?, fechaUltimoCommit: String?, estadoCI: String?) {
        this.ultimoCommit = ultimoCommit
        this.fechaUltimoCommit = fechaUltimoCommit
        this.estadoCI = estadoCI
    }

    fun actualizarUltimoCommit(ultimoCommit: String?, fechaUltimoCommit: String?) {
        this.ultimoCommit = ultimoCommit
        this.fechaUltimoCommit = fechaUltimoCommit
    }

    fun marcarComoTemplate(descripcion: String? = this.descripcion) {
        this.esTemplate = true
        this.descripcion = descripcion
        this.eliminado = false
    }

    fun marcarComoEliminado() {
        this.eliminado = true
    }
}
