package com.ar.edu.unq.unqlassroom.dto.curso.request

import com.ar.edu.unq.unqlassroom.model.Curso
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class CursoRequestDTO(
    @field:NotBlank(message = "El nombre de la materia es obligatorio")
    val materia: String,

    @field:NotNull(message = "El año es obligatorio")
    @field:Min(value = 2000, message = "El año debe ser válido (mayor o igual a 2000)")
    val anio: Int,

    @field:NotNull(message = "El semestre es obligatorio")
    @field:Min(value = 1, message = "El semestre debe ser 1 o 2")
    @field:Max(value = 2, message = "El semestre debe ser 1 o 2")
    val semestre: Int,

    @field:NotNull(message = "La comisión es obligatoria")
    @field:Min(value = 1, message = "La comisión debe ser mayor a 0")
    val comision: Int,
) {
    fun aModelo(): Curso {
        val curso = Curso(
            materia = this.materia,
            anio = this.anio,
            semestre = this.semestre,
            comision = this.comision,
        )
        curso.descripcion = curso.generarDescripcion()
        return curso
    }
}
