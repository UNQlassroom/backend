package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.exception.*
import com.ar.edu.unq.unqlassroom.util.toRepoSlug
import jakarta.persistence.*
import java.time.LocalDate

@Entity
@Table(name = "cursos")
class Curso(

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

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "cursos_docentes",
        joinColumns = [JoinColumn(name = "curso_id")],
        inverseJoinColumns = [JoinColumn(name = "usuario_id")]
    )
    var docentes: MutableList<Usuario> = mutableListOf(),

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

    fun esDocente(username: String?): Boolean {
        if (username.isNullOrBlank()) return false
        return docentes.any { it.username.equals(username, ignoreCase = true) }
    }

    fun validarEsDocente(
        username: String?,
        mensaje: String = "Solo los docentes del curso pueden realizar esta acción"
    ) {
        if (!esDocente(username)) {
            throw NoEsDocenteDelCursoException(mensaje)
        }
    }

    fun agregarDocente(docente: Usuario) {
        validarNoArchivado()
        if (esDocente(docente.username)) {
            throw DocenteDuplicadoException(listOf(docente.username))
        }
        docentes.add(docente)
    }

    fun removerDocente(username: String) {
        validarNoArchivado()
        val normalized = username.trim()
        if (!esDocente(normalized)) {
            throw DocenteNoAsignadoException(normalized)
        }
        val docentesActivos = docentes.distinctBy { it.username.lowercase() }
        if (docentesActivos.size <= 1) {
            throw CursoSinDocentesException()
        }
        docentes.removeIf { it.username.equals(normalized, ignoreCase = true) }
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

    fun generarNombreArchivoExportacion(): String =
        "curso_${materia.toRepoSlug()}_${anio}_c${comision}_unqlassroom.xlsx"
}
