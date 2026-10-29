package com.ar.edu.unq.unqlassroom.dto.asignacion.response

import com.ar.edu.unq.unqlassroom.model.AdvertenciaGit
import com.ar.edu.unq.unqlassroom.model.TipoAdvertenciaGit

data class AdvertenciaGitResponseDTO(
    val id: Long?,
    val commitSha: String,
    val mensajeCommit: String,
    val tipo: TipoAdvertenciaGit,
    val mensajeExplicativo: String,
    val archivosModificados: Int,
    val lineasModificadas: Int,
    val lineasAgregadas: Int,
    val lineasEliminadas: Int,
    val autorUsername: String?,
    val fechaCommit: String?,
) {
    companion object {
        fun desdeModelo(modelo: AdvertenciaGit): AdvertenciaGitResponseDTO = AdvertenciaGitResponseDTO(
            id = modelo.id,
            commitSha = modelo.commitSha,
            mensajeCommit = modelo.mensajeCommit,
            tipo = modelo.tipo,
            mensajeExplicativo = modelo.mensajeExplicativo,
            archivosModificados = modelo.archivosModificados,
            lineasModificadas = modelo.lineasModificadas,
            lineasAgregadas = modelo.lineasAgregadas,
            lineasEliminadas = modelo.lineasEliminadas,
            autorUsername = modelo.autorUsername,
            fechaCommit = modelo.fechaCommit,
        )
    }
}

