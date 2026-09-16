package com.ar.edu.unq.unqlassroom.github

import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.util.removerTildes
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

@Service
class GitHubRepoService(
    private val gitHubAppClient: GitHubAppClient,
    private val properties: GitHubAppProperties,
    private val objectMapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
) {

    @JvmOverloads
    fun createOrgRepository(
        name: String,
        description: String? = null,
        private: Boolean = true,
        autoInit: Boolean = true,
        org: String? = null,
    ): GitHubRepoResponse {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val requestBody = objectMapper.writeValueAsString(
            CreateRepoRequest(
                name = name,
                description = description,
                private = private,
                autoInit = autoInit,
            ),
        )
        return gitHubAppClient.executeInstallationRequest(
            method = "POST",
            path = "/orgs/$targetOrg/repos",
            responseType = GitHubRepoResponse::class.java,
            body = requestBody,
        )
    }

    @JvmOverloads
    fun getRepository(
        repoName: String,
        org: String? = null,
    ): GitHubRepoResponse {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        return gitHubAppClient.executeInstallationRequest(
            method = "GET",
            path = "/repos/$targetOrg/$repoName",
            responseType = GitHubRepoResponse::class.java,
        )
    }


    @JvmOverloads
    fun repositoryExists(
        repoName: String,
        org: String? = null,
    ): Boolean {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        return gitHubAppClient.checkResourceExists("/repos/$targetOrg/$repoName")
    }

    @JvmOverloads
    fun getUltimoCommit(
        repoName: String,
        org: String? = null,
    ): GitHubCommitResponse? {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        return try {
            val commits = gitHubAppClient.executeInstallationRequest(
                method = "GET",
                path = "/repos/$targetOrg/$repoName/commits?per_page=1",
                responseType = Array<GitHubCommitResponse>::class.java,
            )
            commits.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    @JvmOverloads
    fun getEstadoCI(
        repoName: String,
        commitSha: String? = null,
        org: String? = null,
    ): String {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        if (commitSha.isNullOrBlank()) {
            return "sin_ci"
        }

        // 1. Checks API (GitHub Actions / Apps)
        try {
            val checkRuns = gitHubAppClient.executeInstallationRequest(
                method = "GET",
                path = "/repos/$targetOrg/$repoName/commits/$commitSha/check-runs",
                responseType = GitHubCheckRunsResponse::class.java,
            )
            if (checkRuns.totalCount > 0) {
                val runs = checkRuns.checkRuns
                if (runs.any { it.conclusion?.lowercase() in listOf("failure", "timed_out", "cancelled") }) {
                    return "failure"
                }
                if (runs.any { it.status.lowercase() != "completed" }) {
                    return "pending"
                }
                if (runs.all { it.conclusion?.lowercase() in listOf("success", "neutral", "skipped") }) {
                    return "success"
                }
            }
        } catch (_: Exception) {
            // continuar
        }

        // 2. Commit Statuses API
        try {
            val status = gitHubAppClient.executeInstallationRequest(
                method = "GET",
                path = "/repos/$targetOrg/$repoName/commits/$commitSha/status",
                responseType = GitHubCombinedStatusResponse::class.java,
            )
            if (status.totalCount > 0 && status.state.isNotBlank()) {
                return status.state.lowercase()
            }
        } catch (_: Exception) {
            // continuar
        }

        // 3. Workflow Runs API
        try {
            val workflowRuns = gitHubAppClient.executeInstallationRequest(
                method = "GET",
                path = "/repos/$targetOrg/$repoName/actions/runs?head_sha=$commitSha&per_page=5",
                responseType = GitHubWorkflowRunsResponse::class.java,
            )
            if (workflowRuns.totalCount > 0) {
                val runs = workflowRuns.workflowRuns
                if (runs.any { it.conclusion?.lowercase() in listOf("failure", "timed_out", "cancelled") }) {
                    return "failure"
                }
                if (runs.any { it.status.lowercase() != "completed" }) {
                    return "pending"
                }
                if (runs.all { it.conclusion?.lowercase() in listOf("success", "neutral", "skipped") }) {
                    return "success"
                }
            }
        } catch (_: Exception) {
            // continuar
        }

        return "sin_ci"
    }

    @JvmOverloads
    fun obtenerInformacionRepositorio(
        repoName: String,
        org: String? = null,
    ): RepositorioInfo {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val ultimoCommit = getUltimoCommit(repoName, targetOrg)
        val commitMessage = ultimoCommit?.commit?.message
        val commitDate = ultimoCommit?.commit?.committer?.date ?: ultimoCommit?.commit?.author?.date
        val commitSha = ultimoCommit?.sha
        val estadoCI = getEstadoCI(repoName, commitSha, targetOrg)

        val htmlUrl = "https://github.com/$targetOrg/$repoName"

        return RepositorioInfo(
            nombre = repoName,
            htmlUrl = htmlUrl,
            ultimoCommit = commitMessage,
            fechaUltimoCommit = commitDate,
            estadoCI = estadoCI,
        )
    }

    fun generarNombreRepo(curso: Curso, username: String): String =
        "${curso.generarNombreRepo()}_${username.trim().removerTildes()}"

    fun generarDescripcionRepo(curso: com.ar.edu.unq.unqlassroom.model.Curso, username: String): String =
        "Repositorio individual de ${username.trim()} para el curso ${curso.materia} - Año ${curso.anio} - Semestre ${curso.semestre} - Comisión ${curso.comision}"

    private fun requiredOrganization(): String = properties.organization?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("github.app.organization must be configured with the GitHub Organization name")
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateRepoRequest(
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("private") val private: Boolean = true,
    @JsonProperty("auto_init") val autoInit: Boolean = true,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubRepoResponse(
    @JsonProperty("id") val id: Long = 0,
    @JsonProperty("name") val name: String = "",
    @JsonProperty("full_name") val fullName: String = "",
    @JsonProperty("html_url") val htmlUrl: String = "",
    @JsonProperty("clone_url") val cloneUrl: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RepositorioInfo(
    val nombre: String,
    val htmlUrl: String,
    val ultimoCommit: String? = null,
    val fechaUltimoCommit: String? = null,
    val estadoCI: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubCommitResponse(
    @JsonProperty("sha") val sha: String = "",
    @JsonProperty("commit") val commit: GitHubCommitData? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubCommitData(
    @JsonProperty("message") val message: String = "",
    @JsonProperty("author") val author: GitHubCommitAuthor? = null,
    @JsonProperty("committer") val committer: GitHubCommitAuthor? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubCommitAuthor(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("date") val date: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubCheckRunsResponse(
    @JsonProperty("total_count") val totalCount: Int = 0,
    @JsonProperty("check_runs") val checkRuns: List<GitHubCheckRunItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubCheckRunItem(
    @JsonProperty("name") val name: String = "",
    @JsonProperty("status") val status: String = "",
    @JsonProperty("conclusion") val conclusion: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubCombinedStatusResponse(
    @JsonProperty("state") val state: String = "",
    @JsonProperty("total_count") val totalCount: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubWorkflowRunsResponse(
    @JsonProperty("total_count") val totalCount: Int = 0,
    @JsonProperty("workflow_runs") val workflowRuns: List<GitHubWorkflowRunItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubWorkflowRunItem(
    @JsonProperty("status") val status: String = "",
    @JsonProperty("conclusion") val conclusion: String? = null,
)

