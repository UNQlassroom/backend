package com.ar.edu.unq.unqlassroom.controller.dtos

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
)
