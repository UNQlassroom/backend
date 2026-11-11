package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.exception.NombreUsuarioInvalidoException
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

    fun actualizarNombreCompleto(nombre: String, apellido: String) {
        val n = nombre.trim().replace("\\s+".toRegex(), " ")
        val a = apellido.trim().replace("\\s+".toRegex(), " ")

        if (n.isEmpty() || a.isEmpty()) {
            throw NombreUsuarioInvalidoException("El nombre y apellido no puede estar vacío")
        }

        val armado = "$a, $n"
        if (armado.length > 35) {
            throw NombreUsuarioInvalidoException("El nombre y apellido no puede exceder los 35 caracteres")
        }

        val regex = Regex("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ' -]+$")
        if (!regex.matches(n) || !regex.matches(a)) {
            throw NombreUsuarioInvalidoException("El nombre y apellido contiene caracteres inválidos")
        }

        this.nombreCompleto = armado
    }
}
