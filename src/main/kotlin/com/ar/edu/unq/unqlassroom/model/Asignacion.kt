package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.util.toRepoSlug
import jakarta.persistence.*
import java.time.LocalDateTime

enum class TipoAsignacion {
    INDIVIDUAL,
    GRUPAL
}

@Entity
@Table(name = "asignaciones")
class Asignacion(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    var titulo: String,

    @Column(nullable = true)
    var descripcion: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var tipo: TipoAsignacion,

    @Column(nullable = false)
    var templateRepoName: String,

    @Column(nullable = true)
    var fechaLimite: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "curso_id", nullable = false)
    var curso: Curso,

    @OneToMany(mappedBy = "asignacion", cascade = [CascadeType.ALL], orphanRemoval = true)
    var grupos: MutableList<GrupoAsignacion> = mutableListOf()
) {
    init {
        titulo = titulo.trim()
    }

    fun generarNombreRepo(sufijo: String): String {
        val anio = curso.anio
        val semestre = curso.semestre
        val comision = curso.comision
        val materiaSlug = curso.materia.toRepoSlug()
        val tituloSlug = titulo.toRepoSlug()
        val sufijoSlug = sufijo.toRepoSlug()
        return "${anio}s${semestre}_c${comision}_${materiaSlug}_${tituloSlug}_${sufijoSlug}"
    }

    fun generarDescripcionRepo(sufijo: String): String {
        return "Repositorio de asignación '$titulo' ($sufijo) - ${curso.materia} (${curso.anio}s${curso.semestre} comision ${curso.comision})"
    }
}
