package com.ar.edu.unq.unqlassroom.model

import jakarta.persistence.*

@Entity
@Table(name = "usuarios")
class Usuario(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false, unique = true)
    var username: String,

    @Column(nullable = false)
    var esDocente: Boolean = false,

    @Column(nullable = true)
    var email: String? = null,

    @Column(nullable = true)
    var nombreCompleto: String? = null,

    @Column(name = "github_state", nullable = false)
    var githubState: String = "pending",
) {
    fun activarSiCorresponde(estadoRemoto: String): Boolean {
        if (githubState == "pending" && estadoRemoto == "active") {
            githubState = "active"
            return true
        }
        return false
    }
}
