package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.exception.*
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
    var curso: Curso? = null,

    @OneToMany(mappedBy = "asignacion", cascade = [CascadeType.ALL], orphanRemoval = true)
    var grupos: MutableList<GrupoAsignacion> = mutableListOf()
) {
    init {
        titulo = titulo.trim()
    }

    fun asociarACurso(curso: Curso) {
        this.curso = curso
    }

    fun estaVencida(fecha: LocalDateTime = LocalDateTime.now()): Boolean =
        fechaLimite != null && fecha.isAfter(fechaLimite)

    fun validarVencimiento(fecha: LocalDateTime = LocalDateTime.now()) {
        if (estaVencida(fecha)) {
            throw AsignacionVencidaException()
        }
    }

    fun validarEstructuraGrupos() {
        if (tipo == TipoAsignacion.INDIVIDUAL) {
            if (grupos.isNotEmpty()) {
                throw AsignacionIndividualConGruposException()
            }
        } else {
            if (grupos.isEmpty()) {
                throw AsignacionGrupalSinGruposException()
            }
            if (grupos.any { it.integrantes.isEmpty() }) {
                throw GrupoSinIntegrantesException()
            }
            if (grupos.any { it.nombre.isNullOrBlank() }) {
                throw NombreGrupoVacioException()
            }
            val groupNames = grupos.map { it.normalizarNombre() }
            if (groupNames.size != groupNames.distinct().size) {
                throw NombreGrupoDuplicadoException()
            }
            val allMembers = grupos.flatMap { it.integrantes.map { u -> u.username.trim() } }
            if (allMembers.size != allMembers.distinct().size) {
                throw AlumnoEnMultiplesGruposException()
            }
        }
    }

    fun buscarGrupo(grupoId: Long): GrupoAsignacion =
        grupos.find { it.id == grupoId }
            ?: throw GrupoNoPerteneceAAsignacionException()

    fun buscarGrupoPorAlumno(username: String): GrupoAsignacion =
        grupos.find { it.tieneIntegrante(username) }
            ?: throw UsuarioNoPerteneceAGrupoException()

    fun paraVisualizacionDe(username: String, esOwner: Boolean): Asignacion {
        val gruposVisibles = if (esOwner) {
            this.grupos
        } else {
            this.grupos.filter { it.tieneIntegrante(username) }
        }
        return Asignacion(
            id = this.id,
            titulo = this.titulo,
            descripcion = this.descripcion,
            tipo = this.tipo,
            templateRepoName = this.templateRepoName,
            fechaLimite = this.fechaLimite,
            curso = this.curso,
            grupos = gruposVisibles.toMutableList()
        )
    }

    fun generarNombreRepo(sufijo: String): String {
        val c = curso ?: throw IllegalStateException("La asignación no está asociada a ningún curso")
        val anio = c.anio
        val semestre = c.semestre
        val comision = c.comision
        val materiaSlug = c.materia.toRepoSlug()
        val tituloSlug = titulo.toRepoSlug()
        val sufijoSlug = sufijo.toRepoSlug()
        return "${anio}s${semestre}_c${comision}_${materiaSlug}_${tituloSlug}_${sufijoSlug}"
    }

    fun generarDescripcionRepo(sufijo: String): String {
        val c = curso ?: throw IllegalStateException("La asignación no está asociada a ningún curso")
        return "Repositorio de asignación '$titulo' ($sufijo) - ${c.materia} (${c.anio}s${c.semestre} comision ${c.comision})"
    }
}
