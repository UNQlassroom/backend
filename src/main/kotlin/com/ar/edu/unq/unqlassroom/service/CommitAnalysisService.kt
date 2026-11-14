package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.AdvertenciaGit
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.fasterxml.jackson.databind.JsonNode

interface CommitAnalysisService {
    fun analizarCommitsDePush(repositorio: Repositorio, rootNode: JsonNode): List<AdvertenciaGit>
    fun esCommitInitOMerge(mensaje: String, commitNode: JsonNode? = null): Boolean
    fun esMensajeInadecuado(mensaje: String): Boolean
}
