package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.AdvertenciaGitRepository
import com.ar.edu.unq.unqlassroom.repository.GrupoAsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.CommitAnalysisService
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class CommitAnalysisServiceImpl(
    private val advertenciaGitRepository: AdvertenciaGitRepository,
    private val grupoAsignacionRepository: GrupoAsignacionRepository,
    private val usuarioRepository: UsuarioRepository,
    @Value("\${git.analysis.max-files:15}")
    private val maxFiles: Int = 15,
    @Value("\${git.analysis.max-lines:500}")
    private val maxLines: Int = 500,
) : CommitAnalysisService {

    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private val INIT_OR_MERGE_REGEX = Regex(
            "^(merge\\b.*|(init|initial commit|first commit|primer commit|commit inicial)(\\b|:|$).*)",
            RegexOption.IGNORE_CASE
        )

        private val VAGUE_MESSAGE_REGEX = Regex(
            "^(fix|fixes|fixed|cambios?|updates?|wip|tests?|asdf?|qwerty|commits?|avances?|pruebas?|ajustes?|arreglos?|modificaciones|todo|done|ok|\\.+|-+|([a-zA-Z0-9])\\2+)$",
            RegexOption.IGNORE_CASE
        )

        private val CONVENTIONAL_COMMIT_REGEX = Regex(
            "^(feat|fix|docs|style|refactor|perf|test|build|ci|chore|revert)(\\([a-zA-Z0-9_./-]+\\))?!?:\\s+(.+)$",
            RegexOption.IGNORE_CASE
        )
    }

    override fun analizarCommitsDePush(repositorio: Repositorio, rootNode: JsonNode): List<AdvertenciaGit> {
        val advertenciasPersistidas = mutableListOf<AdvertenciaGit>()

        try {
            val commitsNodes = extraerNodosCommits(rootNode)
            if (commitsNodes.isEmpty()) {
                return emptyList()
            }

            val grupo = try {
                grupoAsignacionRepository.findByRepositorioNombreIgnoreCase(repositorio.nombre)
            } catch (e: Exception) {
                logger.warn("No se pudo obtener GrupoAsignacion para el repositorio '{}': {}", repositorio.nombre, e.message)
                null
            }
            val asignacion = grupo?.asignacion
            val fallbackAuthor = extraerAutorGlobal(rootNode)

            for ((index, commitNode) in commitsNodes.withIndex()) {
                try {
                    val metricas = extraerMetricasCommit(commitNode, rootNode, index, commitsNodes.size, fallbackAuthor)
                    val nuevasAdvertencias = evaluarCommit(metricas, repositorio, grupo, asignacion)

                    for (advertencia in nuevasAdvertencias) {
                        persistirAdvertenciaSegura(advertencia, repositorio, advertenciasPersistidas)
                    }
                } catch (e: Exception) {
                    logger.error(
                        "Error al analizar commit individual en repositorio '{}': {}",
                        repositorio.nombre,
                        e.message
                    )
                }
            }
        } catch (e: Exception) {
            logger.error(
                "Error general al analizar commits del evento push para el repositorio '{}': {}",
                repositorio.nombre,
                e.message
            )
        }

        return advertenciasPersistidas
    }

    override fun esCommitInitOMerge(mensaje: String, commitNode: JsonNode?): Boolean {
        if (commitNode != null) {
            if (commitNode.path("merge").asBoolean(false)) return true
            val parents = commitNode.path("parents")
            if (parents.isArray && parents.size() > 1) return true
        }
        val primeraLinea = mensaje.lineSequence().firstOrNull()?.trim() ?: ""
        if (primeraLinea.isEmpty()) return false
        return INIT_OR_MERGE_REGEX.matches(primeraLinea)
    }

    override fun esMensajeInadecuado(mensaje: String): Boolean {
        val primeraLinea = mensaje.lineSequence().firstOrNull()?.trim() ?: ""
        if (primeraLinea.length < 5) return true
        if (VAGUE_MESSAGE_REGEX.matches(primeraLinea)) return true

        val matchResult = CONVENTIONAL_COMMIT_REGEX.matchEntire(primeraLinea) ?: return true
        val descripcion = matchResult.groupValues[3].trim()
        if (descripcion.length < 3 || VAGUE_MESSAGE_REGEX.matches(descripcion)) {
            return true
        }
        return false
    }

    private fun evaluarCommit(
        metricas: CommitMetrics,
        repositorio: Repositorio,
        grupo: GrupoAsignacion?,
        asignacion: Asignacion?,
    ): List<AdvertenciaGit> {
        val resultado = mutableListOf<AdvertenciaGit>()
        val alumno = resolverAlumno(metricas.autorUsername, grupo)
        val esInitOMerge = esCommitInitOMerge(metricas.mensaje, metricas.nodo)

        // 1. Validación de tamaño (omite commits de init o merge)
        if (!esInitOMerge && (metricas.archivosModificados > maxFiles || metricas.lineasModificadas > maxLines)) {
            val explicacion = "Detectamos un commit grande (+${metricas.lineasModificadas} líneas / ${metricas.archivosModificados} archivos). " +
                "Se recomienda partir los cambios en commits atómicos centrados en una sola responsabilidad."

            resultado.add(
                AdvertenciaGit(
                    commitSha = metricas.sha,
                    mensajeCommit = metricas.mensaje,
                    tipo = TipoAdvertenciaGit.COMMIT_NO_ATOMICO,
                    mensajeExplicativo = explicacion,
                    archivosModificados = metricas.archivosModificados,
                    lineasAgregadas = metricas.lineasAgregadas,
                    lineasEliminadas = metricas.lineasEliminadas,
                    lineasModificadas = metricas.lineasModificadas,
                    autorUsername = metricas.autorUsername ?: alumno?.username,
                    fechaCommit = metricas.timestamp,
                    repositorio = repositorio,
                    grupoAsignacion = grupo,
                    asignacion = asignacion,
                    alumno = alumno,
                )
            )
        }

        // 2. Validación de mensaje con regex (sugiriendo Conventional Commits)
        if (!esInitOMerge && esMensajeInadecuado(metricas.mensaje)) {
            val primeraLinea = metricas.mensaje.lineSequence().firstOrNull()?.trim() ?: ""
            val explicacion = "El mensaje del commit ('$primeraLinea') es ambiguo o no sigue buenas prácticas. " +
                "Se recomienda usar el formato Conventional Commits (feat:, fix:, docs:, test:) con una descripción clara."

            resultado.add(
                AdvertenciaGit(
                    commitSha = metricas.sha,
                    mensajeCommit = metricas.mensaje,
                    tipo = TipoAdvertenciaGit.MENSAJE_INADECUADO,
                    mensajeExplicativo = explicacion,
                    archivosModificados = metricas.archivosModificados,
                    lineasAgregadas = metricas.lineasAgregadas,
                    lineasEliminadas = metricas.lineasEliminadas,
                    lineasModificadas = metricas.lineasModificadas,
                    autorUsername = metricas.autorUsername ?: alumno?.username,
                    fechaCommit = metricas.timestamp,
                    repositorio = repositorio,
                    grupoAsignacion = grupo,
                    asignacion = asignacion,
                    alumno = alumno,
                )
            )
        }

        return resultado
    }

    private fun persistirAdvertenciaSegura(
        advertencia: AdvertenciaGit,
        repositorio: Repositorio,
        acumulador: MutableList<AdvertenciaGit>,
    ) {
        try {
            val repoId = repositorio.id
            if (repoId != null && advertenciaGitRepository.existsByRepositorioIdAndCommitShaAndTipo(
                    repoId,
                    advertencia.commitSha,
                    advertencia.tipo
                )
            ) {
                return
            }

            val guardada = advertenciaGitRepository.save(advertencia)
            acumulador.add(guardada)
        } catch (e: Exception) {
            logger.error(
                "No se pudo persistir la advertencia de Git ({}) para el commit '{}' en '{}': {}",
                advertencia.tipo,
                advertencia.commitSha,
                repositorio.nombre,
                e.message
            )
        }
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

    private fun extraerNodosCommits(rootNode: JsonNode): List<JsonNode> {
        val commitsNode = rootNode.path("commits")
        if (commitsNode.isArray && commitsNode.size() > 0) {
            return commitsNode.toList()
        }

        val headCommit = rootNode.path("head_commit")
        if (headCommit.isObject && !headCommit.isEmpty) {
            return listOf(headCommit)
        }

        if (rootNode.has("message") || rootNode.has("commit_message")) {
            return listOf(rootNode)
        }

        return emptyList()
    }

    private fun extraerMetricasCommit(
        commitNode: JsonNode,
        rootNode: JsonNode,
        index: Int,
        totalCommits: Int,
        fallbackAuthor: String?,
    ): CommitMetrics {
        val esUnicoOUltimo = index == totalCommits - 1
        val headCommitNode = rootNode.path("head_commit")

        val sha = commitNode.firstNonBlankText("id", "sha", "commit_sha")
            ?: (if (esUnicoOUltimo) headCommitNode.firstNonBlankText("id", "sha") else null)
            ?: rootNode.firstNonBlankText("after", "sha", "commit_sha")
            ?: "commit-$index-${commitNode.path("message").asText("").hashCode()}"

        val mensaje = commitNode.firstNonBlankText("message", "commit_message")
            ?: commitNode.path("commit").firstNonBlankText("message")
            ?: (if (esUnicoOUltimo) headCommitNode.firstNonBlankText("message") else null)
            ?: ""

        val timestamp = commitNode.firstNonBlankText("timestamp", "date", "fecha")
            ?: (if (esUnicoOUltimo) headCommitNode.firstNonBlankText("timestamp", "date") else null)
            ?: rootNode.firstNonBlankText("timestamp", "fecha")

        val autorUsername = commitNode.path("author").firstNonBlankText("username", "login", "name")
            ?: commitNode.path("committer").firstNonBlankText("username", "login", "name")
            ?: commitNode.firstNonBlankText("author_username", "autor", "author")
            ?: fallbackAuthor

        val archivosModificados = extraerCantidadArchivos(commitNode, if (esUnicoOUltimo) rootNode else null)
        val (lineasAgregadas, lineasEliminadas, lineasModificadas) = extraerMetricasLineas(
            commitNode,
            if (esUnicoOUltimo) rootNode else null
        )

        return CommitMetrics(
            sha = sha,
            mensaje = mensaje,
            timestamp = timestamp,
            autorUsername = autorUsername,
            archivosModificados = archivosModificados,
            lineasAgregadas = lineasAgregadas,
            lineasEliminadas = lineasEliminadas,
            lineasModificadas = lineasModificadas,
            nodo = commitNode,
        )
    }

    private fun extraerCantidadArchivos(commitNode: JsonNode, rootFallback: JsonNode?): Int {
        val archivosUnicos = mutableSetOf<String>()
        for (campo in listOf("added", "removed", "modified")) {
            val arr = commitNode.path(campo)
            if (arr.isArray) {
                arr.forEach { item ->
                    val nombre = if (item.isTextual) item.asText() else item.path("filename").asText("")
                    if (nombre.isNotBlank()) archivosUnicos.add(nombre)
                }
            }
        }
        if (archivosUnicos.isNotEmpty()) {
            return archivosUnicos.size
        }

        val filesNode = commitNode.path("files")
        if (filesNode.isArray && filesNode.size() > 0) {
            return filesNode.size()
        }
        if (filesNode.isNumber) {
            return filesNode.asInt(0)
        }

        val directoEnCommit = commitNode.firstPositiveInt(
            "files_changed",
            "archivos_modificados",
            "archivos_tocados",
            "total_files",
            "changed_files"
        ) ?: commitNode.path("stats").firstPositiveInt("files", "files_changed", "archivos_modificados")

        if (directoEnCommit != null) return directoEnCommit

        if (rootFallback != null) {
            val directoEnRoot = rootFallback.firstPositiveInt(
                "files_changed",
                "archivos_modificados",
                "archivos_tocados",
                "total_files",
                "changed_files"
            ) ?: rootFallback.path("stats").firstPositiveInt("files", "files_changed", "archivos_modificados")
            if (directoEnRoot != null) return directoEnRoot
        }

        return 0
    }

    private fun extraerMetricasLineas(commitNode: JsonNode, rootFallback: JsonNode?): Triple<Int, Int, Int> {
        val statsNode = commitNode.path("stats")

        var agregadas = commitNode.firstPositiveInt("additions", "lines_added", "lineas_agregadas", "insertions")
            ?: statsNode.firstPositiveInt("additions", "lines_added", "lineas_agregadas", "insertions")
            ?: 0

        var eliminadas = commitNode.firstPositiveInt("deletions", "lines_removed", "lineas_eliminadas")
            ?: statsNode.firstPositiveInt("deletions", "lines_removed", "lineas_eliminadas")
            ?: 0

        var totalDirecto = commitNode.firstPositiveInt("total_lines", "lines_modified", "lineas_modificadas", "changes")
            ?: statsNode.firstPositiveInt("total", "total_lines", "lines_modified", "lineas_modificadas")
            ?: 0

        // Si las líneas vienen desglosadas dentro del array 'files'
        val filesNode = commitNode.path("files")
        if (agregadas == 0 && eliminadas == 0 && totalDirecto == 0 && filesNode.isArray) {
            var sumAdd = 0
            var sumDel = 0
            var sumChanges = 0
            filesNode.forEach { fileItem ->
                sumAdd += fileItem.path("additions").asInt(0)
                sumDel += fileItem.path("deletions").asInt(0)
                sumChanges += fileItem.path("changes").asInt(0)
            }
            agregadas = sumAdd
            eliminadas = sumDel
            totalDirecto = sumChanges
        }

        // Fallback al nodo raíz (por ejemplo cuando el script de CI envía las métricas en el root del payload)
        if (agregadas == 0 && eliminadas == 0 && totalDirecto == 0 && rootFallback != null) {
            val rootStats = rootFallback.path("stats")
            agregadas = rootFallback.firstPositiveInt("additions", "lines_added", "lineas_agregadas", "insertions")
                ?: rootStats.firstPositiveInt("additions", "lines_added", "lineas_agregadas", "insertions")
                ?: 0
            eliminadas = rootFallback.firstPositiveInt("deletions", "lines_removed", "lineas_eliminadas")
                ?: rootStats.firstPositiveInt("deletions", "lines_removed", "lineas_eliminadas")
                ?: 0
            totalDirecto = rootFallback.firstPositiveInt("total_lines", "lines_modified", "lineas_modificadas", "changes")
                ?: rootStats.firstPositiveInt("total", "total_lines", "lines_modified", "lineas_modificadas")
                ?: 0
        }

        val suma = agregadas + eliminadas
        val total = if (suma > 0) maxOf(suma, totalDirecto) else totalDirecto
        if (agregadas == 0 && eliminadas == 0 && total > 0) {
            agregadas = total
        }

        return Triple(agregadas, eliminadas, total)
    }

    private fun extraerAutorGlobal(rootNode: JsonNode): String? {
        return rootNode.path("pusher").firstNonBlankText("name", "username", "login")
            ?: rootNode.path("sender").firstNonBlankText("login", "username")
            ?: rootNode.path("head_commit").path("author").firstNonBlankText("username", "login", "name")
            ?: rootNode.firstNonBlankText("author", "autor", "username")
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

    private fun JsonNode.firstPositiveInt(vararg fieldNames: String): Int? {
        if (this.isMissingNode || this.isNull) return null
        for (field in fieldNames) {
            val child = this.path(field)
            if (child.isNumber) {
                return child.asInt()
            } else if (child.isTextual) {
                val parsed = child.asText().trim().toIntOrNull()
                if (parsed != null) return parsed
            }
        }
        return null
    }

    private data class CommitMetrics(
        val sha: String,
        val mensaje: String,
        val timestamp: String?,
        val autorUsername: String?,
        val archivosModificados: Int,
        val lineasAgregadas: Int,
        val lineasEliminadas: Int,
        val lineasModificadas: Int,
        val nodo: JsonNode,
    )
}
