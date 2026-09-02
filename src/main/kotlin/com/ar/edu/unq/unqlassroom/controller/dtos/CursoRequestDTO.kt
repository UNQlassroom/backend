package com.ar.edu.unq.unqlassroom.controller.dtos

import com.ar.edu.unq.unqlassroom.model.Curso

data class CursoRequestDTO(
    val materia: String,
    val anio: Int,
    val semestre: Int,
    val comision: Int,
) {
    fun aModelo(): Curso {
        val curso = Curso(
            materia = this.materia,
            anio = this.anio,
            semestre = this.semestre,
            comision = this.comision,
        )
        curso.descripcion = curso.generarDescripcionTeam()
        return curso
    }
}