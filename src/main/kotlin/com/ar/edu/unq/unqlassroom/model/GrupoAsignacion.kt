package com.ar.edu.unq.unqlassroom.model

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
    var asignacion: Asignacion,

    @ManyToMany
    @JoinTable(
        name = "grupo_asignacion_usuarios",
        joinColumns = [JoinColumn(name = "grupo_id")],
        inverseJoinColumns = [JoinColumn(name = "usuario_id")]
    )
    var integrantes: MutableList<Usuario> = mutableListOf(),

    @OneToOne(cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "repositorio_id", nullable = false)
    var repositorio: Repositorio,

    @Column(nullable = false)
    var entregada: Boolean = false,

    @Column(nullable = true)
    var fechaEntrega: LocalDateTime? = null,

    @Column(nullable = true)
    var releaseUrl: String? = null,

    @Column(nullable = false)
    var cantidadEntregas: Int = 0
) {
    fun normalizarNombre(): String =
        nombre?.removerTildes()?.lowercase()?.trim()?.replace("\\s+".toRegex(), "_") ?: ""
}
