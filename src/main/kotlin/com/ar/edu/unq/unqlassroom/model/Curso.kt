package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.util.removerTildes
import jakarta.persistence.*

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

    @Column(nullable = true)
    var githubTeamId: Long? = null,

    @Column(nullable = true)
    var githubTeamSlug: String? = null
) {
    init {
        materia = materia.trim()
    }

    fun generarNombreTeam(): String {
        val mat = materia.removerTildes().lowercase().trim().replace("\\s+".toRegex(), "_")
        return "${anio}s${semestre}_c${comision}_${mat}"
    }

    fun generarDescripcionTeam(): String = "Curso de $materia - Año $anio - Semestre $semestre - Comisión $comision"
}