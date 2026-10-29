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

    @Column(nullable = true)
    var ciRunId: Long? = null,

    @Column(nullable = true, length = 500)
    var ciEtapaFallida: String? = null,

    @Column(nullable = true, columnDefinition = "TEXT")
    var ciLogError: String? = null,

    @Column(nullable = true, length = 1000)
    var ciRunUrl: String? = null,

    @Column(nullable = false)
    var esTemplate: Boolean = false,

    @Column(name = "ultimo_commit_sha", nullable = true)
    var ultimoCommitSha: String? = null,

    @Column(name = "template_aprobado", nullable = false)
    var templateAprobado: Boolean = false,

    @Column(nullable = true, length = 1000)
    var descripcion: String? = null,

    @Column(nullable = false)
    var eliminado: Boolean = false,
) {
    fun actualizarInfo(ultimoCommit: String?, fechaUltimoCommit: String?, estadoCI: String?) {
        this.ultimoCommit = ultimoCommit
        this.fechaUltimoCommit = fechaUltimoCommit
        this.estadoCI = estadoCI
        if (estadoCI == "success" || estadoCI == "sin_ci") {
            limpiarFallaCI()
        }
    }

    fun registrarFallaCI(
        etapaFallida: String?,
        logError: String?,
        runUrl: String?,
        runId: Long? = null,
    ) {
        this.estadoCI = "failure"
        this.ciEtapaFallida = etapaFallida
        this.ciLogError = logError
        this.ciRunUrl = runUrl
        if (runId != null && runId > 0) {
            this.ciRunId = runId
        }
    }

    fun limpiarFallaCI() {
        this.ciRunId = null
        this.ciEtapaFallida = null
        this.ciLogError = null
        this.ciRunUrl = null
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

    fun estaTemplateAprobadoConCommit(commitSha: String?): Boolean {
        return this.esTemplate &&
            this.templateAprobado &&
            !commitSha.isNullOrBlank() &&
            this.ultimoCommitSha == commitSha
    }

    fun aprobarTemplate(commitSha: String) {
        this.esTemplate = true
        this.ultimoCommitSha = commitSha
        this.templateAprobado = true
        this.eliminado = false
    }

    fun invalidarAprobacionTemplate(nuevoCommitSha: String? = this.ultimoCommitSha) {
        this.ultimoCommitSha = nuevoCommitSha
        this.templateAprobado = false
    }

    fun marcarComoEliminado() {
        this.eliminado = true
    }
}
