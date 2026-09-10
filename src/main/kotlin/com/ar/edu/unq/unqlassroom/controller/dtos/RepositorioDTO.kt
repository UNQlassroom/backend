package com.ar.edu.unq.unqlassroom.controller.dtos

import com.ar.edu.unq.unqlassroom.model.Repositorio

data class RepositorioDTO(
    val nombre: String,
    val htmlUrl: String,
    val ultimoCommit: String? = null,
    val fechaUltimoCommit: String? = null,
    val estadoCI: String? = null,
) {
    companion object {
        fun desdeModelo(modelo: Repositorio): RepositorioDTO {
            return RepositorioDTO(
                nombre = modelo.nombre,
                htmlUrl = modelo.htmlUrl,
                ultimoCommit = modelo.ultimoCommit,
                fechaUltimoCommit = modelo.fechaUltimoCommit,
                estadoCI = modelo.estadoCI,
            )
        }
    }
}
