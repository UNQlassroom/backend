package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.GrupoAsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.ReporteLinterRepository
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.LinterCIService
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class LinterCIServiceImpl(
    private val reporteLinterRepository: ReporteLinterRepository,
    private val grupoAsignacionRepository: GrupoAsignacionRepository,
    private val usuarioRepository: UsuarioRepository,
) : LinterCIService {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val CHECKSTYLE_FILE_REGEX = Regex(
            """<file\b[^>]*\bname\s*=\s*["']([^"']+)["'][^>]*>([\s\S]*?)</file>""",
            RegexOption.IGNORE_CASE
        )
        private val CHECKSTYLE_ERROR_REGEX = Regex(
            """<error\b([^>]*)/?>""",
            RegexOption.IGNORE_CASE
        )
        private val XML_ATTR_LINE_REGEX = Regex("""\bline\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
        private val XML_ATTR_COLUMN_REGEX = Regex("""\bcolumn\s*=\s*["'](\d+)["']""", RegexOption.IGNORE_CASE)
        private val XML_ATTR_SEVERITY_REGEX = Regex("""\bseverity\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        private val XML_ATTR_MESSAGE_REGEX = Regex("""\bmessage\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        private val XML_ATTR_SOURCE_REGEX = Regex("""\bsource\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        private val XML_ATTR_RULE_REGEX = Regex("""\brule\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

        private val RUNNER_PREFIX_REGEX = Regex("""^(?:/github/workspace/|/home/runner/work/[^/]+/[^/]+/|/workspace/|[A-Za-z]:[\\/]+[^\\/]+[\\/]+work[\\/]+[^\\/]+[\\/]+[^\\/]+[\\/]+)""")
    }

    override fun contieneDatosLinter(rootNode: JsonNode): Boolean {
        val candidateNodes = obtenerNodosCandidatos(rootNode)
        val directFields = listOf(
            "incidencias", "incidencias_linter", "incidenciasLinter",
            "linter_issues", "linterIssues", "violations", "findings", "diagnostics",
            "cantidad_errores", "cantidadErrores", "error_count", "errorCount",
            "cantidad_advertencias", "cantidadAdvertencias", "warning_count", "warningCount",
            "checkstyle_xml", "checkstyleXml", "detekt_xml", "detektXml",
            "ktlint_xml", "ktlintXml", "linter_xml", "linterXml",
            "files", "runs"
        )

        if (candidateNodes.any { node -> directFields.any { field -> node.hasNonNull(field) } }) {
            return true
        }

        val containers = listOf(
            "linter", "linter_report", "linterReport", "reporte_linter", "reporteLinter",
            "static_analysis", "staticAnalysis", "checkstyle", "ktlint", "detekt", "eslint", "sarif"
        )
        val workflowNode = rootNode.path("workflow_run")
        return containers.any { key ->
            (rootNode.hasNonNull(key) && (rootNode.path(key).isObject || rootNode.path(key).isArray || rootNode.path(key).isTextual)) ||
                (workflowNode.hasNonNull(key) && (workflowNode.path(key).isObject || workflowNode.path(key).isArray || workflowNode.path(key).isTextual))
        }
    }

    override fun procesarReporteDeEvento(repositorio: Repositorio, rootNode: JsonNode): ReporteLinter? {
        if (!contieneDatosLinter(rootNode)) {
            return null
        }

        return try {
            val commitSha = extraerCommitSha(rootNode, repositorio)
            val runId = extraerRunId(rootNode)
            val autorUsername = extraerAutorUsername(rootNode)
            val herramienta = extraerHerramienta(rootNode)

            val grupo = try {
                grupoAsignacionRepository.findByRepositorioNombreIgnoreCase(repositorio.nombre)
            } catch (e: Exception) {
                logger.warn("No se pudo obtener GrupoAsignacion para el repositorio '{}': {}", repositorio.nombre, e.message)
                null
            }
            val asignacion = grupo?.asignacion
            val alumno = resolverAlumno(autorUsername, grupo)

            val datosReporte = extraerDatosLinter(rootNode, repositorio.nombre)

            val repoId = repositorio.id
            val existente = if (repoId != null) {
                reporteLinterRepository.findByRepositorioIdAndCommitSha(repoId, commitSha)
            } else {
                null
            }

            val reporte = if (existente != null) {
                existente.actualizarDesdeReporte(
                    cantidadErrores = datosReporte.cantidadErrores,
                    cantidadAdvertencias = datosReporte.cantidadAdvertencias,
                    nuevasIncidencias = datosReporte.incidencias,
                    herramienta = herramienta,
                    autorUsername = autorUsername,
                    runId = runId,
                    grupoAsignacion = grupo,
                    asignacion = asignacion,
                    alumno = alumno,
                )
                existente
            } else {
                val nuevo = ReporteLinter(
                    commitSha = commitSha,
                    herramienta = herramienta,
                    cantidadErrores = datosReporte.cantidadErrores,
                    cantidadAdvertencias = datosReporte.cantidadAdvertencias,
                    totalIncidencias = datosReporte.cantidadErrores + datosReporte.cantidadAdvertencias,
                    autorUsername = autorUsername ?: alumno?.username,
                    runId = runId,
                    repositorio = repositorio,
                    grupoAsignacion = grupo,
                    asignacion = asignacion,
                    alumno = alumno,
                )
                nuevo.reemplazarIncidencias(datosReporte.incidencias)
                nuevo
            }

            val guardado = reporteLinterRepository.save(reporte)
            logger.info(
                "Reporte de linter persistido para repo '{}' (commit {}): errores={}, advertencias={}, incidencias={}",
                repositorio.nombre,
                commitSha,
                guardado.cantidadErrores,
                guardado.cantidadAdvertencias,
                guardado.incidencias.size
            )
            guardado
        } catch (e: Exception) {
            logger.error(
                "Error al procesar o persistir reporte de linter para repositorio '{}': {}",
                repositorio.nombre,
                e.message
            )
            null
        }
    }

    fun extraerDatosLinter(rootNode: JsonNode, repoNombre: String? = null): DatosReporteLinter {
        val candidateNodes = obtenerNodosCandidatos(rootNode)

        val incidenciasDirectas = extraerIncidenciasDeJsonDirecto(candidateNodes, repoNombre)
        val incidenciasPorArchivo = if (incidenciasDirectas.isEmpty()) {
            extraerIncidenciasPorArchivo(rootNode, candidateNodes, repoNombre)
        } else {
            emptyList()
        }
        val incidenciasSarif = if (incidenciasDirectas.isEmpty() && incidenciasPorArchivo.isEmpty()) {
            extraerIncidenciasDeSarif(rootNode, candidateNodes, repoNombre)
        } else {
            emptyList()
        }
        val incidenciasXml = if (incidenciasDirectas.isEmpty() && incidenciasPorArchivo.isEmpty() && incidenciasSarif.isEmpty()) {
            extraerIncidenciasDeXml(rootNode, candidateNodes, repoNombre)
        } else {
            emptyList()
        }

        val incidencias = when {
            incidenciasDirectas.isNotEmpty() -> incidenciasDirectas
            incidenciasPorArchivo.isNotEmpty() -> incidenciasPorArchivo
            incidenciasSarif.isNotEmpty() -> incidenciasSarif
            else -> incidenciasXml
        }

        val erroresDeLista = incidencias.count { it.severidad == SeveridadLinter.ERROR }
        val advertenciasDeLista = incidencias.count { it.severidad == SeveridadLinter.WARNING }

        val erroresExplicitos = candidateNodes.firstNonNegativeInt(
            "cantidad_errores", "cantidadErrores", "errores", "errors", "error_count", "errorCount"
        )
        val advertenciasExplicitas = candidateNodes.firstNonNegativeInt(
            "cantidad_advertencias", "cantidadAdvertencias", "advertencias", "warnings", "warning_count", "warningCount"
        )

        val cantidadErrores = maxOf(erroresDeLista, erroresExplicitos ?: 0)
        val cantidadAdvertencias = maxOf(advertenciasDeLista, advertenciasExplicitas ?: 0)

        return DatosReporteLinter(
            cantidadErrores = cantidadErrores,
            cantidadAdvertencias = cantidadAdvertencias,
            incidencias = incidencias,
        )
    }

    private fun extraerIncidenciasDeJsonDirecto(
        candidateNodes: List<JsonNode>,
        repoNombre: String?,
    ): List<IncidenciaLinter> {
        val arrayFields = listOf(
            "incidencias", "incidencias_linter", "incidenciasLinter",
            "issues", "linter_issues", "linterIssues",
            "violations", "findings", "diagnostics", "results", "warnings", "errors"
        )

        for (node in candidateNodes) {
            for (field in arrayFields) {
                val arr = node.path(field)
                if (arr.isArray && arr.size() > 0) {
                    val first = arr.first()
                    if (first.isObject && esNodoIncidenciaIndividual(first)) {
                        val defaultSeveridad = when (field.lowercase()) {
                            "errors", "errores" -> SeveridadLinter.ERROR
                            else -> SeveridadLinter.WARNING
                        }
                        val parsed = arr.mapNotNull { item ->
                            parsearIncidenciaJson(item, defaultArchivo = null, defaultSeveridad = defaultSeveridad, repoNombre = repoNombre)
                        }
                        if (parsed.isNotEmpty()) {
                            return parsed
                        }
                    }
                }
            }
        }
        return emptyList()
    }

    private fun extraerIncidenciasPorArchivo(
        rootNode: JsonNode,
        candidateNodes: List<JsonNode>,
        repoNombre: String?,
    ): List<IncidenciaLinter> {
        val result = mutableListOf<IncidenciaLinter>()
        val fileArrayContainers = mutableListOf<JsonNode>()

        for (node in candidateNodes) {
            for (field in listOf("files", "archivos", "results", "report", "linter", "eslint", "ktlint")) {
                val child = node.path(field)
                if (child.isArray) {
                    fileArrayContainers.add(child)
                }
            }
        }
        for (field in listOf("linter", "linter_report", "eslint", "ktlint")) {
            val child = rootNode.path(field)
            if (child.isArray) {
                fileArrayContainers.add(child)
            }
        }

        for (filesArray in fileArrayContainers) {
            for (fileNode in filesArray) {
                if (!fileNode.isObject) continue
                val filePath = fileNode.firstNonBlankText("file", "archivo", "filePath", "file_path", "path", "name")
                    ?: continue
                val messagesNode = listOf("messages", "lint_errors", "errors", "warnings", "incidencias", "issues", "violations")
                    .map { fileNode.path(it) }
                    .firstOrNull { it.isArray }

                if (messagesNode != null) {
                    for (msgNode in messagesNode) {
                        val inc = parsearIncidenciaJson(
                            item = msgNode,
                            defaultArchivo = filePath,
                            defaultSeveridad = SeveridadLinter.WARNING,
                            repoNombre = repoNombre,
                        )
                        if (inc != null) {
                            result.add(inc)
                        }
                    }
                }
            }
            if (result.isNotEmpty()) {
                return result
            }
        }

        return result
    }

    private fun extraerIncidenciasDeSarif(
        rootNode: JsonNode,
        candidateNodes: List<JsonNode>,
        repoNombre: String?,
    ): List<IncidenciaLinter> {
        val sarifContainers = candidateNodes + listOf(
            rootNode.path("sarif"),
            rootNode.path("workflow_run").path("sarif")
        ).filter { it.isObject }

        val result = mutableListOf<IncidenciaLinter>()
        for (container in sarifContainers) {
            val runs = container.path("runs")
            if (!runs.isArray) continue
            for (run in runs) {
                val results = run.path("results")
                if (!results.isArray) continue
                for (sarifResult in results) {
                    val ruleId = sarifResult.firstNonBlankText("ruleId", "rule_id", "rule") ?: "general"
                    val level = sarifResult.firstNonBlankText("level", "severity")
                    val severidad = parsearSeveridad(level, SeveridadLinter.WARNING)
                    val message = sarifResult.path("message").firstNonBlankText("text", "markdown")
                        ?: sarifResult.firstNonBlankText("message")

                    val firstLocation = sarifResult.path("locations").takeIf { it.isArray && it.size() > 0 }?.first()
                    val physLoc = firstLocation?.path("physicalLocation")
                    val rawUri = physLoc?.path("artifactLocation")?.firstNonBlankText("uri")
                        ?: sarifResult.firstNonBlankText("file", "archivo")
                        ?: continue
                    val startLine = physLoc?.path("region")?.firstNonNegativeInt("startLine", "line")
                        ?: sarifResult.firstNonNegativeInt("line", "linea")
                        ?: 1
                    val startColumn = physLoc?.path("region")?.firstNonNegativeInt("startColumn", "column")

                    result.add(
                        IncidenciaLinter(
                            archivo = normalizarRutaArchivo(rawUri, repoNombre),
                            linea = maxOf(1, startLine),
                            columna = startColumn?.takeIf { it > 0 },
                            severidad = severidad,
                            regla = normalizarRegla(ruleId),
                            mensaje = message,
                        )
                    )
                }
            }
            if (result.isNotEmpty()) {
                return result
            }
        }
        return result
    }

    private fun extraerIncidenciasDeXml(
        rootNode: JsonNode,
        candidateNodes: List<JsonNode>,
        repoNombre: String?,
    ): List<IncidenciaLinter> {
        val xmlRaw = candidateNodes.firstNonBlankText(
            "checkstyle_xml", "checkstyleXml", "detekt_xml", "detektXml",
            "ktlint_xml", "ktlintXml", "linter_xml", "linterXml", "xml"
        ) ?: listOf("checkstyle", "detekt", "ktlint", "linter")
            .mapNotNull { key ->
                rootNode.path(key).takeIf { it.isTextual }?.asText()?.trim()?.takeIf { it.startsWith("<") }
            }
            .firstOrNull()

        if (xmlRaw.isNullOrBlank()) return emptyList()
        return parsearCheckstyleXml(xmlRaw, repoNombre)
    }

    fun parsearCheckstyleXml(xmlContent: String, repoNombre: String? = null): List<IncidenciaLinter> {
        val incidencias = mutableListOf<IncidenciaLinter>()
        val fileMatches = CHECKSTYLE_FILE_REGEX.findAll(xmlContent)

        for (fileMatch in fileMatches) {
            val rawFileName = fileMatch.groupValues[1].trim()
            val fileBody = fileMatch.groupValues[2]
            val archivo = normalizarRutaArchivo(rawFileName, repoNombre)

            val errorMatches = CHECKSTYLE_ERROR_REGEX.findAll(fileBody)
            for (errorMatch in errorMatches) {
                val attrs = errorMatch.groupValues[1]
                val linea = XML_ATTR_LINE_REGEX.find(attrs)?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val columna = XML_ATTR_COLUMN_REGEX.find(attrs)?.groupValues?.get(1)?.toIntOrNull()
                val severityRaw = XML_ATTR_SEVERITY_REGEX.find(attrs)?.groupValues?.get(1)
                val messageRaw = XML_ATTR_MESSAGE_REGEX.find(attrs)?.groupValues?.get(1)?.let { decodificarEntidadesXml(it) }
                val sourceRaw = XML_ATTR_SOURCE_REGEX.find(attrs)?.groupValues?.get(1)
                    ?: XML_ATTR_RULE_REGEX.find(attrs)?.groupValues?.get(1)
                    ?: "checkstyle"

                incidencias.add(
                    IncidenciaLinter(
                        archivo = archivo,
                        linea = maxOf(1, linea),
                        columna = columna?.takeIf { it > 0 },
                        severidad = parsearSeveridad(severityRaw, SeveridadLinter.WARNING),
                        regla = normalizarRegla(sourceRaw),
                        mensaje = messageRaw?.takeIf { it.isNotBlank() },
                    )
                )
            }
        }
        return incidencias
    }

    private fun esNodoIncidenciaIndividual(node: JsonNode): Boolean {
        val fileKeys = listOf("archivo", "file", "filePath", "file_path", "path")
        val ruleOrMsgKeys = listOf("regla", "rule", "ruleId", "rule_id", "code", "source", "mensaje", "message", "description")
        return fileKeys.any { node.hasNonNull(it) } || ruleOrMsgKeys.any { node.hasNonNull(it) }
    }

    private fun parsearIncidenciaJson(
        item: JsonNode,
        defaultArchivo: String?,
        defaultSeveridad: SeveridadLinter,
        repoNombre: String?,
    ): IncidenciaLinter? {
        if (!item.isObject) return null

        val rawArchivo = item.firstNonBlankText("archivo", "file", "filePath", "file_path", "path")
            ?: defaultArchivo
            ?: return null

        val linea = item.firstNonNegativeInt("linea", "line", "lineNumber", "line_number", "start_line", "startLine")
            ?: 1
        val columna = item.firstNonNegativeInt("columna", "column", "col", "columnNumber", "column_number", "startColumn")

        val severidadNode = listOf("severidad", "severity", "level", "tipo", "type")
            .map { item.path(it) }
            .firstOrNull { !it.isMissingNode && !it.isNull }

        val severidad = when {
            severidadNode == null -> defaultSeveridad
            severidadNode.isNumber -> if (severidadNode.asInt() >= 2) SeveridadLinter.ERROR else SeveridadLinter.WARNING
            severidadNode.isTextual -> parsearSeveridad(severidadNode.asText(), defaultSeveridad)
            else -> defaultSeveridad
        }

        val rawRegla = item.firstNonBlankText("regla", "rule", "ruleId", "rule_id", "code", "source", "check")
        val mensaje = item.firstNonBlankText("mensaje", "message", "description", "detalle", "msg", "summary")

        val reglaFinal = when {
            !rawRegla.isNullOrBlank() -> normalizarRegla(rawRegla)
            !mensaje.isNullOrBlank() -> mensaje.take(120)
            else -> "regla-desconocida"
        }

        return IncidenciaLinter(
            archivo = normalizarRutaArchivo(rawArchivo, repoNombre),
            linea = maxOf(1, linea),
            columna = columna?.takeIf { it > 0 },
            severidad = severidad,
            regla = reglaFinal,
            mensaje = mensaje,
        )
    }

    private fun parsearSeveridad(raw: String?, defaultSeveridad: SeveridadLinter): SeveridadLinter {
        if (raw.isNullOrBlank()) return defaultSeveridad
        return when (raw.trim().lowercase()) {
            "error", "err", "fatal", "blocker", "critical", "high", "2" -> SeveridadLinter.ERROR
            "warning", "warn", "advertencia", "info", "information", "note", "style", "convention", "refactor", "code_smell", "low", "medium", "1" -> SeveridadLinter.WARNING
            else -> defaultSeveridad
        }
    }

    private fun normalizarRutaArchivo(rawPath: String, repoNombre: String?): String {
        var cleaned = rawPath.trim().removePrefix("file://").removePrefix("file:/").replace('\\', '/')
        cleaned = RUNNER_PREFIX_REGEX.replace(cleaned, "")
        if (!repoNombre.isNullOrBlank()) {
            val repoPrefix = "${repoNombre.trim('/')}/"
            val idx = cleaned.indexOf(repoPrefix, ignoreCase = true)
            if (idx >= 0) {
                cleaned = cleaned.substring(idx + repoPrefix.length)
            }
        }
        cleaned = cleaned.removePrefix("./").removePrefix("/")
        return cleaned.ifBlank { rawPath.trim() }
    }

    private fun normalizarRegla(rawRule: String): String {
        val trimmed = rawRule.trim()
        if (trimmed.contains('.') && (trimmed.startsWith("com.") || trimmed.startsWith("org.") || trimmed.startsWith("detekt."))) {
            val lastSegment = trimmed.substringAfterLast('.')
            if (lastSegment.isNotBlank()) {
                return lastSegment.removeSuffix("Check")
            }
        }
        return trimmed
    }

    private fun decodificarEntidadesXml(texto: String): String {
        return texto
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
    }

    private fun extraerCommitSha(rootNode: JsonNode, repositorio: Repositorio): String {
        val candidateNodes = obtenerNodosCandidatos(rootNode)
        val sha = candidateNodes.firstNonBlankText("commit_sha", "commitSha", "head_sha", "headSha", "sha", "commit")
            ?: rootNode.path("workflow_run").path("head_commit").firstNonBlankText("id", "sha")
            ?: repositorio.ultimoCommit
        return if (!sha.isNullOrBlank()) sha else "unknown"
    }

    private fun extraerRunId(rootNode: JsonNode): Long? {
        val candidateNodes = obtenerNodosCandidatos(rootNode)
        for (node in candidateNodes) {
            for (field in listOf("run_id", "runId", "id")) {
                val value = node.path(field)
                if (value.isNumber && value.asLong() > 0L) {
                    if (field == "id" && node === rootNode && !rootNode.has("workflow_run")) continue
                    return value.asLong()
                }
                if (value.isTextual) {
                    val parsed = value.asText().trim().toLongOrNull()
                    if (parsed != null && parsed > 0L) return parsed
                }
            }
        }
        return null
    }

    private fun extraerHerramienta(rootNode: JsonNode): String? {
        val candidateNodes = obtenerNodosCandidatos(rootNode)
        val explicita = candidateNodes.firstNonBlankText("herramienta", "tool", "linter_name", "linterName", "analyzer")
        if (!explicita.isNullOrBlank()) return explicita

        return when {
            rootNode.hasNonNull("ktlint") || candidateNodes.any { it.hasNonNull("ktlint_xml") || it.hasNonNull("ktlintXml") } -> "ktlint"
            rootNode.hasNonNull("detekt") || candidateNodes.any { it.hasNonNull("detekt_xml") || it.hasNonNull("detektXml") } -> "detekt"
            rootNode.hasNonNull("checkstyle") || candidateNodes.any { it.hasNonNull("checkstyle_xml") || it.hasNonNull("checkstyleXml") } -> "checkstyle"
            rootNode.hasNonNull("eslint") -> "eslint"
            rootNode.hasNonNull("sarif") || rootNode.hasNonNull("runs") -> "sarif"
            else -> null
        }
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
            rootNode.path("linter"),
            rootNode.path("linter_report"),
            rootNode.path("linterReport"),
            rootNode.path("reporte_linter"),
            rootNode.path("reporteLinter"),
            rootNode.path("static_analysis"),
            rootNode.path("staticAnalysis"),
            rootNode.path("checkstyle"),
            rootNode.path("ktlint"),
            rootNode.path("detekt"),
            rootNode.path("eslint"),
            rootNode.path("sarif"),
            rootNode.path("workflow_run"),
            rootNode.path("workflow_run").path("linter"),
            rootNode.path("workflow_run").path("linter_report"),
            rootNode.path("workflow_run").path("reporte_linter"),
            rootNode.path("workflow_run").path("static_analysis"),
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

    data class DatosReporteLinter(
        val cantidadErrores: Int,
        val cantidadAdvertencias: Int,
        val incidencias: List<IncidenciaLinter>,
    )
}
