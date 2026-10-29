package com.ar.edu.unq.unqlassroom.dto.curso.response

import com.ar.edu.unq.unqlassroom.model.Repositorio

data class RepositorioDTO(
    val nombre: String,
    val htmlUrl: String,
    val ultimoCommit: String? = null,
    val fechaUltimoCommit: String? = null,
    val estadoCI: String? = null,
    val ciEtapaFallida: String? = null,
    val ciLogError: String? = null,
    val ciRunUrl: String? = null,
) {
    companion object {
        fun desdeModelo(modelo: Repositorio): RepositorioDTO {
            return RepositorioDTO(
                nombre = modelo.nombre,
                htmlUrl = modelo.htmlUrl,
                ultimoCommit = modelo.ultimoCommit,
                fechaUltimoCommit = modelo.fechaUltimoCommit,
                estadoCI = modelo.estadoCI,
                ciEtapaFallida = modelo.ciEtapaFallida,
                ciLogError = modelo.ciLogError,
                ciRunUrl = modelo.ciRunUrl,
            )
        }
    }
}
