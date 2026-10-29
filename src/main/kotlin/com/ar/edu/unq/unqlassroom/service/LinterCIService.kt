package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.ReporteLinter
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.fasterxml.jackson.databind.JsonNode

interface LinterCIService {
    fun contieneDatosLinter(rootNode: JsonNode): Boolean
    fun procesarReporteDeEvento(repositorio: Repositorio, rootNode: JsonNode): ReporteLinter?
}
