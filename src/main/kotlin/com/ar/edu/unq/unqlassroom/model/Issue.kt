package com.ar.edu.unq.unqlassroom.model

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(
    name = "issues",
    uniqueConstraints = [UniqueConstraint(columnNames = ["repositorio_id", "numero"])]
)
class Issue(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    var numero: Int,

    @Column(nullable = false, length = 500)
    var titulo: String,

    @Column(nullable = false, length = 1000)
    var htmlUrl: String,

    @Column(nullable = false)
    var autor: String,

    @Column(nullable = false)
    var estadoGitHub: String, // "open" o "closed"

    @Column(nullable = false)
    var cantComentarios: Int = 0,

    @Column(nullable = false)
    var fechaCreacion: String,

    @Column(nullable = false)
    var fechaActualizacion: String,

    @Column(nullable = true)
    var fechaCierre: String? = null,

    @Column(nullable = false)
    var eliminado: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repositorio_id", nullable = false)
    var repositorio: Repositorio,
) {
    fun actualizarDesdeWebhook(
        titulo: String,
        estadoGitHub: String,
        cantComentarios: Int,
        fechaActualizacion: String,
        fechaCierre: String?
    ) {
        this.titulo = titulo
        this.estadoGitHub = estadoGitHub
        this.cantComentarios = cantComentarios
        this.fechaActualizacion = fechaActualizacion
        this.fechaCierre = fechaCierre
        this.eliminado = false
    }

    fun marcarComoEliminado() {
        this.eliminado = true
    }

    fun tieneCommitsPosteriores(fechaCommit: String? = repositorio.fechaUltimoCommit): Boolean {
        if (fechaCommit.isNullOrBlank() || fechaCreacion.isBlank()) return false
        return try {
            val commitInstant = Instant.parse(fechaCommit)
            val issueInstant = Instant.parse(fechaCreacion)
            commitInstant.isAfter(issueInstant)
        } catch (_: Exception) {
            false
        }
    }

    fun calcularEstado(fechaCommit: String? = repositorio.fechaUltimoCommit): EstadoIssue {
        if (estadoGitHub.equals("closed", ignoreCase = true)) {
            return EstadoIssue.RESUELTO
        }
        return if (tieneCommitsPosteriores(fechaCommit) || cantComentarios > 0) {
            EstadoIssue.ACTUALIZADO
        } else {
            EstadoIssue.PENDIENTE
        }
    }
}
