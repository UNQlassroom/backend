package com.ar.edu.unq.unqlassroom.integration.github.service

import com.ar.edu.unq.unqlassroom.exception.GitHubApiException
import com.ar.edu.unq.unqlassroom.integration.github.client.GitHubAppClient
import com.ar.edu.unq.unqlassroom.integration.github.config.GitHubAppProperties

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.Base64

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
        isTemplate: Boolean = false,
        org: String? = null,
    ): GitHubRepoResponse {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val requestBody = objectMapper.writeValueAsString(
            CreateRepoRequest(
                name = name,
                description = description,
                private = private,
                autoInit = autoInit,
                isTemplate = isTemplate,
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
    fun createTemplateRepository(
        name: String,
        description: String? = null,
        org: String? = null,
    ): GitHubRepoResponse {
        return createOrgRepository(
            name = name,
            description = description,
            private = true,
            autoInit = true,
            isTemplate = true,
            org = org,
        )
    }

    @JvmOverloads
    fun createRepositoryFromTemplate(
        templateRepoName: String,
        newRepoName: String,
        description: String? = null,
        private: Boolean = true,
        org: String? = null,
    ): GitHubRepoResponse {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val requestBody = objectMapper.writeValueAsString(
            GenerateRepoFromTemplateRequest(
                owner = targetOrg,
                name = newRepoName,
                description = description,
                private = private,
                includeAllBranches = false,
            )
        )
        return gitHubAppClient.executeInstallationRequest(
            method = "POST",
            path = "/repos/$targetOrg/$templateRepoName/generate",
            responseType = GitHubRepoResponse::class.java,
            body = requestBody,
        )
    }

    @JvmOverloads
    fun listTemplateRepositories(
        org: String? = null,
    ): List<GitHubRepoResponse> {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val repos = gitHubAppClient.executeInstallationRequest(
            method = "GET",
            path = "/orgs/$targetOrg/repos?type=all&per_page=100",
            responseType = Array<GitHubRepoResponse>::class.java,
        )
        return repos.filter { it.isTemplate }
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
    fun getFileContent(
        repoName: String,
        path: String,
        org: String? = null,
    ): GitHubFileContentResponse? {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val normalizedPath = path.trim().trimStart('/')
        return try {
            gitHubAppClient.executeInstallationRequest(
                method = "GET",
                path = "/repos/$targetOrg/$repoName/contents/$normalizedPath",
                responseType = GitHubFileContentResponse::class.java,
            )
        } catch (_: Exception) {
            null
        }
    }

    @JvmOverloads
    fun createOrUpdateFile(
        repoName: String,
        path: String,
        content: String,
        message: String,
        sha: String? = null,
        branch: String? = null,
        org: String? = null,
    ): GitHubContentUpdateResponse {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val normalizedPath = path.trim().trimStart('/')
        val encodedContent = Base64.getEncoder().encodeToString(content.toByteArray(StandardCharsets.UTF_8))

        fun executePut(targetSha: String?): GitHubContentUpdateResponse {
            val requestBody = objectMapper.writeValueAsString(
                CreateOrUpdateFileRequest(
                    message = message,
                    content = encodedContent,
                    sha = targetSha?.takeIf { it.isNotBlank() },
                    branch = branch?.takeIf { it.isNotBlank() },
                )
            )
            return gitHubAppClient.executeInstallationRequest(
                method = "PUT",
                path = "/repos/$targetOrg/$repoName/contents/$normalizedPath",
                responseType = GitHubContentUpdateResponse::class.java,
                body = requestBody,
            )
        }

        return try {
            executePut(sha)
        } catch (ex: GitHubApiException) {
            if (sha.isNullOrBlank() && (ex.statusCode == 422 || ex.statusCode == 409)) {
                val existingSha = getFileContent(repoName, normalizedPath, targetOrg)?.sha
                if (!existingSha.isNullOrBlank()) {
                    return executePut(existingSha)
                }
            }
            throw ex
        }
    }

    @JvmOverloads
    fun getRepositoryTree(
        repoName: String,
        treeSha: String,
        recursive: Boolean = true,
        org: String? = null,
    ): GitHubGitTreeResponse? {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val query = if (recursive) "?recursive=1" else ""
        return try {
            gitHubAppClient.executeInstallationRequest(
                method = "GET",
                path = "/repos/$targetOrg/$repoName/git/trees/$treeSha$query",
                responseType = GitHubGitTreeResponse::class.java,
            )
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

    @JvmOverloads
    fun createRelease(
        repoName: String,
        tagName: String,
        name: String? = null,
        body: String? = null,
        targetCommitish: String? = null,
        org: String? = null,
    ): GitHubReleaseResponse {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val requestBody = objectMapper.writeValueAsString(
            CreateReleaseRequest(
                tagName = tagName,
                name = name,
                body = body,
                targetCommitish = targetCommitish,
                draft = false,
                prerelease = false,
            )
        )
        return gitHubAppClient.executeInstallationRequest(
            method = "POST",
            path = "/repos/$targetOrg/$repoName/releases",
            responseType = GitHubReleaseResponse::class.java,
            body = requestBody,
        )
    }

    @JvmOverloads
    fun deleteRepository(
        repoName: String,
        org: String? = null,
    ) {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        gitHubAppClient.executeInstallationRequest(
            method = "DELETE",
            path = "/repos/$targetOrg/$repoName",
            responseType = Unit::class.java,
        )
    }

    @JvmOverloads
    fun getWorkflowRunJobs(
        repoName: String,
        runId: Long,
        org: String? = null,
    ): List<GitHubWorkflowJobItem> {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        return try {
            val response = gitHubAppClient.executeInstallationRequest(
                method = "GET",
                path = "/repos/$targetOrg/$repoName/actions/runs/$runId/jobs",
                responseType = GitHubWorkflowJobsResponse::class.java,
            )
            response.jobs
        } catch (_: Exception) {
            emptyList()
        }
    }

    @JvmOverloads
    fun getJobLogs(
        repoName: String,
        jobId: Long,
        org: String? = null,
    ): String? {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        return try {
            gitHubAppClient.downloadTextResource("/repos/$targetOrg/$repoName/actions/jobs/$jobId/logs")
        } catch (_: Exception) {
            null
        }
    }

    @JvmOverloads
    fun obtenerDiagnosticoCorridaFallida(
        repoName: String,
        runId: Long,
        runHtmlUrl: String? = null,
        org: String? = null,
    ): DiagnosticoCIInfo {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val defaultRunUrl = runHtmlUrl?.takeIf { it.isNotBlank() }
            ?: "https://github.com/$targetOrg/$repoName/actions/runs/$runId"

        val failureConclusions = setOf("failure", "timed_out", "cancelled", "action_required")
        val jobs = getWorkflowRunJobs(repoName, runId, targetOrg)

        val failedJob = jobs.firstOrNull { job ->
            job.conclusion?.lowercase() in failureConclusions ||
                job.steps.any { step -> step.conclusion?.lowercase() in failureConclusions }
        } ?: jobs.firstOrNull()

        if (failedJob == null) {
            return DiagnosticoCIInfo(
                runId = runId,
                jobName = null,
                etapaFallida = "Ejecución de workflow",
                logError = "La corrida de CI/CD finalizó con errores. Revisá el detalle completo en GitHub Actions.",
                runUrl = defaultRunUrl,
            )
        }

        val failedStep = failedJob.steps.firstOrNull { it.conclusion?.lowercase() in failureConclusions }
        val etapaFallida = failedStep?.name?.takeIf { it.isNotBlank() }
            ?: failedJob.name.takeIf { it.isNotBlank() }
            ?: "Ejecución de workflow"

        val rawLog = if (failedJob.id > 0) {
            getJobLogs(repoName, failedJob.id, targetOrg)
        } else {
            null
        }

        val logError = extraerResumenLogError(
            rawLog = rawLog,
            etapaFallida = etapaFallida,
            jobName = failedJob.name.takeIf { it.isNotBlank() },
        )

        val finalRunUrl = runHtmlUrl?.takeIf { it.isNotBlank() }
            ?: failedJob.htmlUrl?.takeIf { it.isNotBlank() }
            ?: defaultRunUrl

        return DiagnosticoCIInfo(
            runId = runId,
            jobName = failedJob.name.takeIf { it.isNotBlank() },
            etapaFallida = etapaFallida,
            logError = logError,
            runUrl = finalRunUrl,
        )
    }

    @JvmOverloads
    fun obtenerUltimoDiagnosticoCIFallido(
        repoName: String,
        org: String? = null,
    ): DiagnosticoCIInfo? {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val failureConclusions = setOf("failure", "timed_out", "cancelled", "action_required")
        return try {
            val workflowRuns = gitHubAppClient.executeInstallationRequest(
                method = "GET",
                path = "/repos/$targetOrg/$repoName/actions/runs?per_page=5",
                responseType = GitHubWorkflowRunsResponse::class.java,
            )
            val latestRun = workflowRuns.workflowRuns.firstOrNull() ?: return null
            if (latestRun.conclusion?.lowercase() in failureConclusions && latestRun.id > 0) {
                obtenerDiagnosticoCorridaFallida(
                    repoName = repoName,
                    runId = latestRun.id,
                    runHtmlUrl = latestRun.htmlUrl,
                    org = targetOrg,
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun extraerResumenLogError(
        rawLog: String?,
        etapaFallida: String?,
        jobName: String? = null,
    ): String {
        if (rawLog.isNullOrBlank()) {
            return if (!etapaFallida.isNullOrBlank()) {
                val sufijoJob = if (!jobName.isNullOrBlank() && !jobName.equals(etapaFallida, ignoreCase = true)) {
                    " (job '$jobName')"
                } else {
                    ""
                }
                "Fallo en la etapa '$etapaFallida'$sufijoJob."
            } else {
                "La corrida de CI/CD finalizó con errores."
            }
        }

        val timestampRegex = Regex("""^\uFEFF?\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z\s?""")
        val ansiRegex = Regex("""\u001B\[[;\d]*[A-Za-z]""")

        val cleanedLines = rawLog.lines()
            .map { line ->
                line.replace(timestampRegex, "")
                    .replace(ansiRegex, "")
                    .trimEnd()
            }
            .filter { it.isNotBlank() }

        if (cleanedLines.isEmpty()) {
            return if (!etapaFallida.isNullOrBlank()) {
                "Fallo en la etapa '$etapaFallida'."
            } else {
                "La corrida de CI/CD finalizó con errores."
            }
        }

        val stepGroupIndex = if (!etapaFallida.isNullOrBlank()) {
            cleanedLines.indexOfLast { line ->
                line.startsWith("##[group]", ignoreCase = true) &&
                    line.contains(etapaFallida, ignoreCase = true)
            }
        } else {
            -1
        }

        val lastErrorMarkerIndex = cleanedLines.indexOfLast { it.startsWith("##[error]", ignoreCase = true) }
        val lastGroupBeforeErrorIndex = if (lastErrorMarkerIndex >= 0) {
            cleanedLines.subList(0, lastErrorMarkerIndex + 1)
                .indexOfLast { it.startsWith("##[group]", ignoreCase = true) }
        } else {
            cleanedLines.indexOfLast { it.startsWith("##[group]", ignoreCase = true) }
        }

        val sectionStartIndex = when {
            stepGroupIndex >= 0 -> stepGroupIndex
            lastGroupBeforeErrorIndex >= 0 -> lastGroupBeforeErrorIndex
            else -> 0
        }

        val sectionLines = cleanedLines.subList(sectionStartIndex, cleanedLines.size)
            .filterNot { it.equals("##[endgroup]", ignoreCase = true) }
            .map { line ->
                when {
                    line.startsWith("##[group]", ignoreCase = true) ->
                        "$ " + line.removePrefix("##[group]").removePrefix("##[GROUP]").trim()
                    line.startsWith("##[error]", ignoreCase = true) ->
                        "[ERROR] " + line.removePrefix("##[error]").removePrefix("##[ERROR]").trim()
                    else -> line
                }
            }

        val maxLines = 50
        val maxChars = 4000

        val relevantLines = if (sectionLines.size > maxLines) {
            val errorKeywords = listOf(
                "[ERROR]",
                "FAILURE",
                "Exception",
                "Error:",
                "error:",
                "ERR!",
                "FAIL ",
                "COMPILATION ERROR",
                "Tests run:",
                "AssertionFailedError",
            )
            val firstErrorIdx = sectionLines.indexOfFirst { line ->
                errorKeywords.any { kw -> line.contains(kw) }
            }
            if (firstErrorIdx >= 0) {
                val startIdx = (firstErrorIdx - 3).coerceAtLeast(0)
                sectionLines.subList(startIdx, sectionLines.size).takeLast(maxLines)
            } else {
                sectionLines.takeLast(maxLines)
            }
        } else {
            sectionLines
        }

        val joined = relevantLines.joinToString("\n")
        return if (joined.length > maxChars) {
            "...\n" + joined.takeLast(maxChars).trimStart()
        } else {
            joined
        }
    }

    private fun requiredOrganization(): String = properties.organization?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("github.app.organization must be configured with the GitHub Organization name")
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateRepoRequest(
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("private") val private: Boolean = true,
    @JsonProperty("auto_init") val autoInit: Boolean = true,
    @JsonProperty("is_template") val isTemplate: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GenerateRepoFromTemplateRequest(
    @JsonProperty("owner") val owner: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("private") val private: Boolean = true,
    @JsonProperty("include_all_branches") val includeAllBranches: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubRepoResponse(
    @JsonProperty("id") val id: Long = 0,
    @JsonProperty("name") val name: String = "",
    @JsonProperty("full_name") val fullName: String = "",
    @JsonProperty("html_url") val htmlUrl: String = "",
    @JsonProperty("clone_url") val cloneUrl: String = "",
    @JsonProperty("is_template") val isTemplate: Boolean = false,
    @JsonProperty("description") val description: String? = null,
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
    @JsonProperty("id") val id: Long = 0,
    @JsonProperty("status") val status: String = "",
    @JsonProperty("conclusion") val conclusion: String? = null,
    @JsonProperty("html_url") val htmlUrl: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubWorkflowJobsResponse(
    @JsonProperty("total_count") val totalCount: Int = 0,
    @JsonProperty("jobs") val jobs: List<GitHubWorkflowJobItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubWorkflowJobItem(
    @JsonProperty("id") val id: Long = 0,
    @JsonProperty("name") val name: String = "",
    @JsonProperty("status") val status: String = "",
    @JsonProperty("conclusion") val conclusion: String? = null,
    @JsonProperty("html_url") val htmlUrl: String? = null,
    @JsonProperty("steps") val steps: List<GitHubWorkflowStepItem> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubWorkflowStepItem(
    @JsonProperty("name") val name: String = "",
    @JsonProperty("status") val status: String = "",
    @JsonProperty("conclusion") val conclusion: String? = null,
    @JsonProperty("number") val number: Int = 0,
)

data class DiagnosticoCIInfo(
    val runId: Long,
    val jobName: String? = null,
    val etapaFallida: String,
    val logError: String,
    val runUrl: String,
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateReleaseRequest(
    @JsonProperty("tag_name") val tagName: String,
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("body") val body: String? = null,
    @JsonProperty("target_commitish") val targetCommitish: String? = null,
    @JsonProperty("draft") val draft: Boolean = false,
    @JsonProperty("prerelease") val prerelease: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubReleaseResponse(
    @JsonProperty("id") val id: Long = 0,
    @JsonProperty("tag_name") val tagName: String = "",
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("html_url") val htmlUrl: String = "",
    @JsonProperty("created_at") val createdAt: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateOrUpdateFileRequest(
    @JsonProperty("message") val message: String,
    @JsonProperty("content") val content: String,
    @JsonProperty("sha") val sha: String? = null,
    @JsonProperty("branch") val branch: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubFileContentResponse(
    @JsonProperty("name") val name: String = "",
    @JsonProperty("path") val path: String = "",
    @JsonProperty("sha") val sha: String = "",
    @JsonProperty("encoding") val encoding: String? = null,
    @JsonProperty("content") val content: String? = null,
) {
    fun decodedContent(): String {
        val raw = content?.takeIf { it.isNotBlank() } ?: return ""
        return try {
            String(Base64.getMimeDecoder().decode(raw), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubContentUpdateResponse(
    @JsonProperty("content") val content: GitHubFileContentInfo? = null,
    @JsonProperty("commit") val commit: GitHubContentCommitInfo? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubFileContentInfo(
    @JsonProperty("name") val name: String = "",
    @JsonProperty("path") val path: String = "",
    @JsonProperty("sha") val sha: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubContentCommitInfo(
    @JsonProperty("sha") val sha: String = "",
    @JsonProperty("message") val message: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubGitTreeResponse(
    @JsonProperty("sha") val sha: String = "",
    @JsonProperty("tree") val tree: List<GitHubTreeItem> = emptyList(),
    @JsonProperty("truncated") val truncated: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubTreeItem(
    @JsonProperty("path") val path: String = "",
    @JsonProperty("mode") val mode: String = "",
    @JsonProperty("type") val type: String = "",
    @JsonProperty("sha") val sha: String = "",
)
