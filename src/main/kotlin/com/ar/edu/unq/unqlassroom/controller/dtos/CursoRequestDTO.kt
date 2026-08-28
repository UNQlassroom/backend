package com.ar.edu.unq.unqlassroom.controller.dtos

import com.ar.edu.unq.unqlassroom.model.Curso

data class CursoRequestDTO(
    val materia: String,
    val anio: Int,
    val semestre: Int,
    val comision: Int,
    val descripcion: String
) {
    fun aModelo(): Curso = Curso(
        materia = this.materia,
        anio = this.anio,
        semestre = this.semestre,
        comision = this.comision,
        descripcion = this.descripcion
    )
}