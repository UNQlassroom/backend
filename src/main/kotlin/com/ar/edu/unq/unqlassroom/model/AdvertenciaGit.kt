package com.ar.edu.unq.unqlassroom.model

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "advertencias_git",
    uniqueConstraints = [UniqueConstraint(columnNames = ["repositorio_id", "commit_sha", "tipo"])]
)
class AdvertenciaGit(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "commit_sha", nullable = false)
    var commitSha: String,

    @Column(name = "mensaje_commit", nullable = false, length = 1000)
    var mensajeCommit: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var tipo: TipoAdvertenciaGit,

    @Column(name = "mensaje_explicativo", nullable = false, length = 1000)
    var mensajeExplicativo: String,

    @Column(name = "archivos_modificados", nullable = false)
    var archivosModificados: Int = 0,

    @Column(name = "lineas_agregadas", nullable = false)
    var lineasAgregadas: Int = 0,

    @Column(name = "lineas_eliminadas", nullable = false)
    var lineasEliminadas: Int = 0,

    @Column(name = "lineas_modificadas", nullable = false)
    var lineasModificadas: Int = 0,

    @Column(name = "autor_username", nullable = true)
    var autorUsername: String? = null,

    @Column(name = "fecha_commit", nullable = true)
    var fechaCommit: String? = null,

    @Column(name = "fecha_creacion", nullable = false)
    var fechaCreacion: LocalDateTime = LocalDateTime.now(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repositorio_id", nullable = false)
    var repositorio: Repositorio,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_asignacion_id", nullable = true)
    var grupoAsignacion: GrupoAsignacion? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignacion_id", nullable = true)
    var asignacion: Asignacion? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumno_id", nullable = true)
    var alumno: Usuario? = null,
)

