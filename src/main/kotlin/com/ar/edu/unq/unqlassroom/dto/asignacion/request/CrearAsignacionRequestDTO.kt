package com.ar.edu.unq.unqlassroom.dto.asignacion.request

import com.ar.edu.unq.unqlassroom.model.Asignacion
import com.ar.edu.unq.unqlassroom.model.TipoAsignacion
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class CrearAsignacionRequestDTO(
    @field:NotBlank(message = "El título es obligatorio")
    val titulo: String,

    val descripcion: String? = null,

    @field:NotNull(message = "El tipo de asignación es obligatorio")
    val tipo: TipoAsignacion,

    @field:NotBlank(message = "El repositorio template es obligatorio")
    val templateRepoName: String,

    val fechaLimite: LocalDateTime? = null,

    val grupos: List<CrearGrupoRequestDTO>? = null,
) {
    fun aModelo(): Asignacion {
        val asignacion = Asignacion(
            titulo = this.titulo,
            descripcion = this.descripcion,
            tipo = this.tipo,
            templateRepoName = this.templateRepoName,
            fechaLimite = this.fechaLimite,
        )
        this.grupos?.let { listaGrupos ->
            asignacion.grupos = listaGrupos.map { it.aModelo(asignacion) }.toMutableList()
        }
        return asignacion
    }
}
