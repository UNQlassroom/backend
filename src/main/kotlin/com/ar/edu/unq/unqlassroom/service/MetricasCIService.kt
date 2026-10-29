package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.MetricaCI
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.fasterxml.jackson.databind.JsonNode

interface MetricasCIService {
    fun contieneDatosMetricas(rootNode: JsonNode): Boolean
    fun procesarMetricasDeEvento(repositorio: Repositorio, rootNode: JsonNode): MetricaCI?
}
