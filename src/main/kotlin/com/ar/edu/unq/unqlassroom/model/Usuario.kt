package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.exception.DniInvalidoException
import com.ar.edu.unq.unqlassroom.exception.EmailInvalidoException
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

    @Column(nullable = true, unique = true)
    var email: String? = null,

    @Column(nullable = true)
    var nombreCompleto: String? = null,

    @Column(name = "github_state", nullable = false)
    var githubState: String = "pending",

    @Column(nullable = true, unique = true)
    var dni: String? = null,
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

    fun actualizarEmail(nuevoEmail: String) {
        val emailTrimmed = nuevoEmail.trim()
        if (emailTrimmed.isEmpty()) {
            throw EmailInvalidoException("El correo electrónico no puede estar vacío")
        }

        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@([A-Za-z0-9-]+\\.)+(com|com\\.ar)$", RegexOption.IGNORE_CASE)
        if (!emailRegex.matches(emailTrimmed)) {
            throw EmailInvalidoException("El correo electrónico debe ser válido, contener '@' y finalizar con '.com' o '.com.ar'")
        }

        this.email = emailTrimmed.lowercase()
    }

    fun actualizarDni(nuevoDni: String) {
        val dniTrimmed = nuevoDni.trim()
        if (dniTrimmed.isEmpty()) {
            throw DniInvalidoException("El DNI no puede estar vacío")
        }

        val dniRegex = Regex("^\\d{7,8}$")
        if (!dniRegex.matches(dniTrimmed)) {
            throw DniInvalidoException("El DNI debe contener entre 7 y 8 dígitos numéricos")
        }

        this.dni = dniTrimmed
    }

    fun actualizarDatos(
        nombre: String,
        apellido: String,
        email: String,
        dni: String,
    ) {
        actualizarNombreCompleto(nombre = nombre, apellido = apellido)
        actualizarEmail(nuevoEmail = email)
        actualizarDni(nuevoDni = dni)
    }
}
