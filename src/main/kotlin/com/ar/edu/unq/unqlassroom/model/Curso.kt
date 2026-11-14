package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.exception.*
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "cursos")
class Curso (

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    var materia: String,

    @Column(nullable = false)
    var anio: Int,

    @Column(nullable = false)
    var semestre: Int,

    @Column(nullable = false)
    var comision: Int,

    @Column(nullable = true)
    var descripcion: String? = null,

    @Column(nullable = false, columnDefinition = "boolean default false")
    var archivado: Boolean = false,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = true)
    var owner: Usuario? = null,

    @OneToMany(mappedBy = "curso", cascade = [CascadeType.ALL], orphanRemoval = true)
    var inscripciones: MutableList<Inscripcion> = mutableListOf(),

    @OneToMany(mappedBy = "curso", cascade = [CascadeType.ALL], orphanRemoval = true)
    var asignaciones: MutableList<Asignacion> = mutableListOf()
) {
    init {
        materia = materia.trim()
        if (materia.isBlank()) {
            throw NombreMateriaVacioException()
        }
        if (anio < 2000) {
            throw AnioCursoInvalidoException()
        }
        if (semestre !in 1..2) {
            throw SemestreInvalidoException()
        }
        if (comision <= 0) {
            throw ComisionInvalidaException()
        }
    }

    fun esOwner(username: String?): Boolean =
        owner?.username != null && owner?.username == username && (owner?.esDocente == true)

    fun asignarOwner(docente: Usuario) {
        if (!docente.esDocente) {
            throw UsuarioNoEsDocenteException(docente.username)
        }
        this.owner = docente
    }

    fun validarNoArchivado() {
        if (archivado) {
            throw CursoArchivadoException()
        }
    }

    fun archivar() {
        validarNoArchivado()
        this.archivado = true
    }

    fun fechaFinSemestre(): LocalDate {
        return if (semestre == 1) {
            LocalDate.of(anio, 7, 31)
        } else {
            LocalDate.of(anio, 12, 31)
        }
    }

    fun debeArchivarseAutomaticamente(fechaActual: LocalDate = LocalDate.now()): Boolean {
        if (archivado) return false
        val fechaArchivadoAutomatico = fechaFinSemestre().plusMonths(2)
        return !fechaActual.isBefore(fechaArchivadoAutomatico)
    }

    fun obtenerNombresRepositorios(): List<String> =
        asignaciones.flatMap { asignacion ->
            asignacion.grupos.mapNotNull { it.repositorio?.nombre }
        }.distinct()

    fun generarDescripcion(): String = "Curso de $materia - Año $anio - Semestre $semestre - Comisión $comision"
}
