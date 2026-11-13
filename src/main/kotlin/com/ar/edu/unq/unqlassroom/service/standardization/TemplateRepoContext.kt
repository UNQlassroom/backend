package com.ar.edu.unq.unqlassroom.service.standardization

import com.ar.edu.unq.unqlassroom.integration.github.dto.GitHubFileContentResponse
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService

class TemplateRepoContext(
    val repoName: String,
    val tieneArbol: Boolean,
    val archivosEnArbol: Map<String, String>,
    private val gitHubRepoService: GitHubRepoService,
) {
    var ultimoShaGenerado: String? = null

    fun obtenerArchivo(path: String): GitHubFileContentResponse? =
        if (!tieneArbol || archivosEnArbol.containsKey(path)) {
            gitHubRepoService.getFileContent(repoName, path)
        } else {
            null
        }

    fun existeArchivo(path: String): Boolean =
        if (tieneArbol) {
            archivosEnArbol.containsKey(path)
        } else {
            gitHubRepoService.getFileContent(repoName, path) != null
        }

    fun escribirArchivo(path: String, content: String, message: String, knownSha: String? = null) {
        val targetSha = knownSha ?: archivosEnArbol[path]
        val response = gitHubRepoService.createOrUpdateFile(
            repoName = repoName,
            path = path,
            content = content,
            message = message,
            sha = targetSha,
        )
        val newCommitSha = response.commit?.sha?.takeIf { it.isNotBlank() }
        if (newCommitSha != null) {
            ultimoShaGenerado = newCommitSha
        }
    }
}
