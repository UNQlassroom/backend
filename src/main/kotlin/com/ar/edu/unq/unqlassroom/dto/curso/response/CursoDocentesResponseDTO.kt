package com.ar.edu.unq.unqlassroom.dto.curso.response

import com.ar.edu.unq.unqlassroom.model.Usuario

data class CursoDocentesResponseDTO(
    val cursoId: Long,
    val docentes: List<DocenteResponseDTO>,
) {
    companion object {
        fun desdeModelo(cursoId: Long, docentes: List<Usuario>): CursoDocentesResponseDTO =
            CursoDocentesResponseDTO(
                cursoId = cursoId,
                docentes = docentes.map { DocenteResponseDTO.desdeModelo(it) },
            )
    }
}
