package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.GrupoAsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.MetricaCIRepository
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import kotlin.math.round

@Service
class MetricasCIService(
    private val metricaCIRepository: MetricaCIRepository,
    private val grupoAsignacionRepository: GrupoAsignacionRepository,
    private val usuarioRepository: UsuarioRepository,
    @Value("\${ci.metrics.min-coverage:80.0}")
    private val umbralCoberturaDefault: Double = 80.0,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val JACOCO_LINE_COUNTER_REGEX = Regex(
            """<counter\s+[^>]*type\s*=\s*["']LINE["'][^>]*/>""",
            RegexOption.IGNORE_CASE
        )
        private val XML_ATTR_MISSED_REGEX = Regex("""missed\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
        private val XML_ATTR_COVERED_REGEX = Regex("""covered\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)

        private val JUNIT_TESTSUITE_REGEX = Regex(
            """<testsuite\b([^>]*)>""",
            RegexOption.IGNORE_CASE
        )
        private val XML_ATTR_TESTS_REGEX = Regex("""\btests\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
        private val XML_ATTR_FAILURES_REGEX = Regex("""\bfailures\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
        private val XML_ATTR_ERRORS_REGEX = Regex("""\berrors\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
        private val XML_ATTR_SKIPPED_REGEX = Regex("""\bskipped\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
    }

    fun contieneDatosMetricas(rootNode: JsonNode): Boolean {
        val candidateNodes = obtenerNodosCandidatos(rootNode)
        val metricFieldNames = listOf(
            "cobertura_lineas", "coberturaLineas", "line_coverage", "lineCoverage",
            "coverage_percentage", "porcentaje_cobertura",
            "lines_covered", "lineas_cubiertas", "lineasCubiertas", "linesCovered",
            "lines_missed", "lineas_perdidas", "lineasPerdidas", "linesMissed",
            "tests_run", "testsRun", "tests_ejecutados", "testsEjecutados", "total_tests", "totalTests",
            "tests_passed", "testsPassed", "tests_pasados", "testsPasados",
            "tests_failed", "testsFailed", "tests_fallados", "testsFallados",
            "tests_skipped", "testsSkipped", "tests_salteados", "testsSalteados",
            "jacoco_xml", "jacocoXml", "coverage_xml", "coverageXml",
            "junit_xml", "junitXml", "tests_xml", "testsXml"
        )

        if (candidateNodes.any { node -> metricFieldNames.any { field -> node.hasNonNull(field) } }) {
            return true
        }

        val containerContainers = listOf("metrics", "metricas", "coverage", "cobertura", "jacoco", "tests", "junit", "test_results")
        val workflowNode = rootNode.path("workflow_run")
        return containerContainers.any { key ->
            (rootNode.hasNonNull(key) && (rootNode.path(key).isObject || rootNode.path(key).isNumber || rootNode.path(key).isTextual)) ||
                (workflowNode.hasNonNull(key) && (workflowNode.path(key).isObject || workflowNode.path(key).isNumber || workflowNode.path(key).isTextual))
        }
    }

    fun procesarMetricasDeEvento(repositorio: Repositorio, rootNode: JsonNode): MetricaCI? {
        if (!contieneDatosMetricas(rootNode)) {
            return null
        }

        return try {
            val commitSha = extraerCommitSha(rootNode, repositorio)
            val runId = extraerRunId(rootNode)
            val autorUsername = extraerAutorUsername(rootNode)

            val grupo = try {
                grupoAsignacionRepository.findByRepositorioNombreIgnoreCase(repositorio.nombre)
            } catch (e: Exception) {
                logger.warn("No se pudo obtener GrupoAsignacion para el repositorio '{}': {}", repositorio.nombre, e.message)
                null
            }
            val asignacion = grupo?.asignacion
            val alumno = resolverAlumno(autorUsername, grupo)

            val datosCobertura = extraerDatosCobertura(rootNode)
            val datosTests = extraerDatosTests(rootNode)

            val umbral = extraerUmbralCobertura(rootNode) ?: umbralCoberturaDefault
            val cumpleUmbral = datosCobertura.porcentaje >= umbral

            val repoId = repositorio.id
            val existente = if (repoId != null) {
                metricaCIRepository.findByRepositorioIdAndCommitSha(repoId, commitSha)
            } else {
                null
            }

            val metrica = if (existente != null) {
                existente.actualizarDesdeReporte(
                    coberturaLineas = datosCobertura.porcentaje,
                    lineasCubiertas = datosCobertura.cubiertas,
                    lineasPerdidas = datosCobertura.perdidas,
                    umbralCobertura = umbral,
                    testsEjecutados = datosTests.ejecutados,
                    testsPasados = datosTests.pasados,
                    testsFallados = datosTests.fallados,
                    testsSalteados = datosTests.salteados,
                    autorUsername = autorUsername ?: alumno?.username,
                    runId = runId,
                    grupoAsignacion = grupo,
                    asignacion = asignacion,
                    alumno = alumno,
                )
                existente
            } else {
                MetricaCI(
                    commitSha = commitSha,
                    coberturaLineas = datosCobertura.porcentaje,
                    lineasCubiertas = datosCobertura.cubiertas,
                    lineasPerdidas = datosCobertura.perdidas,
                    umbralCobertura = umbral,
                    cumpleUmbralCobertura = cumpleUmbral,
                    testsEjecutados = datosTests.ejecutados,
                    testsPasados = datosTests.pasados,
                    testsFallados = datosTests.fallados,
                    testsSalteados = datosTests.salteados,
                    autorUsername = autorUsername ?: alumno?.username,
                    runId = runId,
                    repositorio = repositorio,
                    grupoAsignacion = grupo,
                    asignacion = asignacion,
                    alumno = alumno,
                )
            }

            val guardada = metricaCIRepository.save(metrica)
            logger.info(
                "Métricas de CI persistidas para repositorio '{}' (commit '{}'): cobertura={}% ({}/{}), tests={}/{}/{} (pasados/fallados/salteados de {})",
                repositorio.nombre,
                commitSha,
                guardada.coberturaLineas,
                guardada.lineasCubiertas,
                guardada.lineasCubiertas + guardada.lineasPerdidas,
                guardada.testsPasados,
                guardada.testsFallados,
                guardada.testsSalteados,
                guardada.testsEjecutados,
            )
            guardada
        } catch (e: Exception) {
            logger.error(
                "Error al procesar o persistir métricas de CI para el repositorio '{}': {}",
                repositorio.nombre,
                e.message,
                e
            )
            null
        }
    }

    fun parsearJacocoXml(xml: String): DatosCobertura? {
        if (xml.isBlank()) return null
        val matches = JACOCO_LINE_COUNTER_REGEX.findAll(xml).toList()
        if (matches.isEmpty()) return null

        // El último <counter type="LINE" .../> en el reporte JaCoCo corresponde al resumen global del <report>
        val reportCounterTag = matches.last().value
        val missed = XML_ATTR_MISSED_REGEX.find(reportCounterTag)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val covered = XML_ATTR_COVERED_REGEX.find(reportCounterTag)?.groupValues?.get(1)?.toIntOrNull() ?: return null

        val total = covered + missed
        val porcentaje = if (total > 0) {
            redondearDosDecimales((covered.toDouble() / total.toDouble()) * 100.0)
        } else {
            0.0
        }
        return DatosCobertura(porcentaje = porcentaje, cubiertas = covered, perdidas = missed)
    }

    fun parsearJunitXml(xml: String): DatosTests? {
        if (xml.isBlank()) return null
        val matches = JUNIT_TESTSUITE_REGEX.findAll(xml).toList()
        if (matches.isEmpty()) return null

        var totalTests = 0
        var totalFailures = 0
        var totalErrors = 0
        var totalSkipped = 0

        for (match in matches) {
            val attrs = match.groupValues[1]
            totalTests += XML_ATTR_TESTS_REGEX.find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            totalFailures += XML_ATTR_FAILURES_REGEX.find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            totalErrors += XML_ATTR_ERRORS_REGEX.find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            totalSkipped += XML_ATTR_SKIPPED_REGEX.find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }

        val fallados = (totalFailures + totalErrors).coerceAtLeast(0)
        val salteados = totalSkipped.coerceAtLeast(0)
        val ejecutados = maxOf(totalTests, fallados + salteados)
        val pasados = (ejecutados - fallados - salteados).coerceAtLeast(0)

        return DatosTests(
            ejecutados = ejecutados,
            pasados = pasados,
            fallados = fallados,
            salteados = salteados,
        )
    }

    private fun extraerDatosCobertura(rootNode: JsonNode): DatosCobertura {
        val candidateNodes = obtenerNodosCandidatos(rootNode)

        val jacocoXml = candidateNodes.firstNonBlankText("jacoco_xml", "jacocoXml", "coverage_xml", "coverageXml")
        if (!jacocoXml.isNullOrBlank()) {
            val parsed = parsearJacocoXml(jacocoXml)
            if (parsed != null) return parsed
        }

        val coverageNodes = listOf(
            rootNode.path("coverage"),
            rootNode.path("cobertura"),
            rootNode.path("jacoco"),
            rootNode.path("metrics").path("coverage"),
            rootNode.path("metrics").path("jacoco"),
            rootNode.path("workflow_run").path("metrics").path("coverage"),
            rootNode.path("workflow_run").path("coverage"),
        ) + candidateNodes

        val cubiertas = coverageNodes.firstNonNegativeInt(
            "lines_covered", "lineas_cubiertas", "lineasCubiertas", "linesCovered", "covered"
        ) ?: 0
        val perdidas = coverageNodes.firstNonNegativeInt(
            "lines_missed", "lineas_perdidas", "lineasPerdidas", "linesMissed", "missed"
        ) ?: 0

        val porcentajeExplicito = coverageNodes.firstNonNegativeDouble(
            "cobertura_lineas", "coberturaLineas", "line_coverage", "lineCoverage",
            "coverage_percentage", "porcentaje_cobertura", "porcentaje", "percentage", "line"
        ) ?: rootNode.firstNonNegativeDouble("coverage", "cobertura")

        val totalLineas = cubiertas + perdidas
        val porcentajeCalculado = when {
            totalLineas > 0 -> redondearDosDecimales((cubiertas.toDouble() / totalLineas.toDouble()) * 100.0)
            porcentajeExplicito != null -> redondearDosDecimales(porcentajeExplicito.coerceIn(0.0, 100.0))
            else -> 0.0
        }

        return DatosCobertura(
            porcentaje = porcentajeCalculado,
            cubiertas = cubiertas,
            perdidas = perdidas,
        )
    }

    private fun extraerDatosTests(rootNode: JsonNode): DatosTests {
        val candidateNodes = obtenerNodosCandidatos(rootNode)

        val junitXml = candidateNodes.firstNonBlankText("junit_xml", "junitXml", "tests_xml", "testsXml")
        if (!junitXml.isNullOrBlank()) {
            val parsed = parsearJunitXml(junitXml)
            if (parsed != null) return parsed
        }

        val testNodes = listOf(
            rootNode.path("tests"),
            rootNode.path("junit"),
            rootNode.path("test_results"),
            rootNode.path("metrics").path("tests"),
            rootNode.path("metrics").path("junit"),
            rootNode.path("workflow_run").path("metrics").path("tests"),
            rootNode.path("workflow_run").path("tests"),
        ) + candidateNodes

        val ejecutadosRaw = testNodes.firstNonNegativeInt(
            "tests_run", "testsRun", "tests_ejecutados", "testsEjecutados",
            "total_tests", "totalTests", "ejecutados", "total", "run", "tests"
        )
        val pasadosRaw = testNodes.firstNonNegativeInt(
            "tests_passed", "testsPassed", "tests_pasados", "testsPasados",
            "passed", "pasados", "succeeded"
        )
        val falladosRaw = testNodes.firstNonNegativeInt(
            "tests_failed", "testsFailed", "tests_fallados", "testsFallados",
            "failed", "failures", "fallados"
        ) ?: 0
        val erroresRaw = testNodes.firstNonNegativeInt(
            "tests_errors", "testsErrors", "tests_errores", "testsErrores",
            "errors", "errores"
        ) ?: 0
        val salteadosRaw = testNodes.firstNonNegativeInt(
            "tests_skipped", "testsSkipped", "tests_salteados", "testsSalteados",
            "skipped", "salteados", "ignored"
        ) ?: 0

        val fallados = (falladosRaw + erroresRaw).coerceAtLeast(0)
        val salteados = salteadosRaw.coerceAtLeast(0)

        val pasados: Int
        val ejecutados: Int

        if (pasadosRaw != null && ejecutadosRaw == null) {
            pasados = pasadosRaw.coerceAtLeast(0)
            ejecutados = pasados + fallados + salteados
        } else if (ejecutadosRaw != null && pasadosRaw == null) {
            ejecutados = maxOf(ejecutadosRaw, fallados + salteados)
            pasados = (ejecutados - fallados - salteados).coerceAtLeast(0)
        } else if (ejecutadosRaw != null && pasadosRaw != null) {
            pasados = pasadosRaw.coerceAtLeast(0)
            ejecutados = maxOf(ejecutadosRaw, pasados + fallados + salteados)
        } else {
            pasados = 0
            ejecutados = fallados + salteados
        }

        return DatosTests(
            ejecutados = ejecutados,
            pasados = pasados,
            fallados = fallados,
            salteados = salteados,
        )
    }

    private fun extraerCommitSha(rootNode: JsonNode, repositorio: Repositorio): String {
        val candidateNodes = obtenerNodosCandidatos(rootNode)
        val sha = candidateNodes.firstNonBlankText("commit_sha", "commitSha", "head_sha", "headSha", "sha")
            ?: rootNode.path("head_commit").firstNonBlankText("id", "sha")
            ?: rootNode.path("workflow_run").path("head_commit").firstNonBlankText("id", "sha")
            ?: repositorio.ultimoCommit?.trim()?.takeIf { it.isNotBlank() }
            ?: "HEAD"
        return sha.trim()
    }

    private fun extraerRunId(rootNode: JsonNode): Long? {
        val candidateNodes = obtenerNodosCandidatos(rootNode)
        for (node in candidateNodes) {
            for (field in listOf("run_id", "runId")) {
                val child = node.path(field)
                if (child.isNumber && child.asLong() > 0L) return child.asLong()
                if (child.isTextual) {
                    val parsed = child.asText().trim().toLongOrNull()
                    if (parsed != null && parsed > 0L) return parsed
                }
            }
        }
        val wfId = rootNode.path("workflow_run").path("id").asLong(0L)
        return wfId.takeIf { it > 0L }
    }

    private fun extraerAutorUsername(rootNode: JsonNode): String? {
        val candidateNodes = obtenerNodosCandidatos(rootNode)
        return candidateNodes.firstNonBlankText("autor_username", "autorUsername", "author", "autor", "username", "actor")
            ?: rootNode.path("workflow_run").path("actor").firstNonBlankText("login", "username")
            ?: rootNode.path("workflow_run").path("triggering_actor").firstNonBlankText("login", "username")
            ?: rootNode.path("pusher").firstNonBlankText("name", "username", "login")
            ?: rootNode.path("sender").firstNonBlankText("login", "username")
            ?: rootNode.path("head_commit").path("author").firstNonBlankText("username", "login", "name")
    }

    private fun extraerUmbralCobertura(rootNode: JsonNode): Double? {
        val candidateNodes = obtenerNodosCandidatos(rootNode)
        return candidateNodes.firstNonNegativeDouble(
            "umbral_cobertura", "umbralCobertura", "coverage_threshold", "min_coverage", "minCoverage"
        )?.takeIf { it > 0.0 && it <= 100.0 }
    }

    private fun resolverAlumno(autorUsername: String?, grupo: GrupoAsignacion?): Usuario? {
        if (!autorUsername.isNullOrBlank()) {
            val integranteMatch = grupo?.integrantes?.firstOrNull {
                it.username.equals(autorUsername.trim(), ignoreCase = true)
            }
            if (integranteMatch != null) return integranteMatch

            val usuarioEnBd = try {
                usuarioRepository.findByUsernameIgnoreCase(autorUsername.trim())
            } catch (_: Exception) {
                null
            }
            if (usuarioEnBd != null) return usuarioEnBd
        }

        if (grupo != null && grupo.integrantes.size == 1) {
            return grupo.integrantes.first()
        }
        return null
    }

    private fun obtenerNodosCandidatos(rootNode: JsonNode): List<JsonNode> {
        return listOf(
            rootNode,
            rootNode.path("metrics"),
            rootNode.path("metricas"),
            rootNode.path("workflow_run"),
            rootNode.path("workflow_run").path("metrics"),
            rootNode.path("workflow_run").path("metricas"),
        ).filter { !it.isMissingNode && !it.isNull && it.isObject }
    }

    private fun List<JsonNode>.firstNonBlankText(vararg fieldNames: String): String? {
        for (node in this) {
            val found = node.firstNonBlankText(*fieldNames)
            if (found != null) return found
        }
        return null
    }

    private fun JsonNode.firstNonBlankText(vararg fieldNames: String): String? {
        if (this.isMissingNode || this.isNull) return null
        for (field in fieldNames) {
            val child = this.path(field)
            if (child.isTextual) {
                val text = child.asText().trim()
                if (text.isNotEmpty()) return text
            }
        }
        return null
    }

    private fun List<JsonNode>.firstNonNegativeInt(vararg fieldNames: String): Int? {
        for (node in this) {
            val found = node.firstNonNegativeInt(*fieldNames)
            if (found != null) return found
        }
        return null
    }

    private fun JsonNode.firstNonNegativeInt(vararg fieldNames: String): Int? {
        if (this.isMissingNode || this.isNull || !this.isObject) return null
        for (field in fieldNames) {
            val child = this.path(field)
            if (child.isNumber) {
                val value = child.asInt()
                if (value >= 0) return value
            } else if (child.isTextual) {
                val parsed = child.asText().trim().toIntOrNull()
                if (parsed != null && parsed >= 0) return parsed
            }
        }
        return null
    }

    private fun List<JsonNode>.firstNonNegativeDouble(vararg fieldNames: String): Double? {
        for (node in this) {
            val found = node.firstNonNegativeDouble(*fieldNames)
            if (found != null) return found
        }
        return null
    }

    private fun JsonNode.firstNonNegativeDouble(vararg fieldNames: String): Double? {
        if (this.isMissingNode || this.isNull || !this.isObject) return null
        for (field in fieldNames) {
            val child = this.path(field)
            if (child.isNumber) {
                val value = child.asDouble()
                if (value >= 0.0) return value
            } else if (child.isTextual) {
                val cleaned = child.asText().trim().removeSuffix("%").trim().replace(',', '.')
                val parsed = cleaned.toDoubleOrNull()
                if (parsed != null && parsed >= 0.0) return parsed
            }
        }
        return null
    }

    private fun redondearDosDecimales(valor: Double): Double =
        round(valor * 100.0) / 100.0

    data class DatosCobertura(
        val porcentaje: Double,
        val cubiertas: Int,
        val perdidas: Int,
    )

    data class DatosTests(
        val ejecutados: Int,
        val pasados: Int,
        val fallados: Int,
        val salteados: Int,
    )
}

