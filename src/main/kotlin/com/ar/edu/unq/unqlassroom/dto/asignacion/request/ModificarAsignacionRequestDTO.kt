package com.ar.edu.unq.unqlassroom.dto.asignacion.request

import com.ar.edu.unq.unqlassroom.model.TipoAsignacion
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class ModificarAsignacionRequestDTO(
    val descripcion: String? = null,
    val fechaLimite: LocalDateTime? = null,
    val titulo: String? = null,
    val tipo: TipoAsignacion? = null,
    val templateRepoName: String? = null,
)
