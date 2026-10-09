package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.exception.CalificacionInvalidaException
import com.ar.edu.unq.unqlassroom.util.removerTildes
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "grupos_asignacion")
class GrupoAsignacion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = true)
    var nombre: String? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignacion_id", nullable = false)
    var asignacion: Asignacion? = null,

    @ManyToMany
    @JoinTable(
        name = "grupo_asignacion_usuarios",
        joinColumns = [JoinColumn(name = "grupo_id")],
        inverseJoinColumns = [JoinColumn(name = "usuario_id")]
    )
    var integrantes: MutableList<Usuario> = mutableListOf(),

    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "repositorio_id", nullable = true)
    var repositorio: Repositorio? = null,

    @Column(nullable = false)
    var entregada: Boolean = false,

    @Column(nullable = true)
    var fechaEntregada: LocalDateTime? = null,

    @Column(nullable = true)
    var releaseUrl: String? = null,

    @Column(nullable = false)
    var cantidadEntregas: Int = 0,

    @Column(nullable = true)
    var calificacion: Int? = null,

    @Column(nullable = true, length = 2000)
    var observaciones: String? = null,

    @Column(nullable = true)
    var fechaCalificacion: LocalDateTime? = null,
) {
    fun calificar(nota: Int, observaciones: String?) {
        if (nota < 1 || nota > 10) {
            throw CalificacionInvalidaException()
        }
        this.calificacion = nota
        this.observaciones = observaciones
        this.fechaCalificacion = LocalDateTime.now()
    }

    fun registrarEntrega(releaseUrl: String? = null) {
        this.cantidadEntregas += 1
        this.entregada = true
        this.fechaEntregada = LocalDateTime.now()
        this.releaseUrl = releaseUrl
    }

    fun tieneIntegrante(username: String): Boolean =
        integrantes.any { it.username.equals(username.trim(), ignoreCase = true) }

    fun generarProximoTagNameRelease(): String = "entrega-v${cantidadEntregas + 1}"

    fun generarNombreRelease(tituloAsignacion: String): String =
        "Entrega v${cantidadEntregas} - $tituloAsignacion"

    fun generarCuerpoRelease(username: String): String =
        "Entrega realizada por $username el ${fechaEntregada ?: LocalDateTime.now()}"

    fun normalizarNombre(): String =
        nombre?.removerTildes()?.lowercase()?.trim()?.replace("\\s+".toRegex(), "_") ?: ""
}
