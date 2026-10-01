package com.ar.edu.unq.unqlassroom.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.stereotype.Service

@Service
class GitHubIssueService(
    private val gitHubAppClient: GitHubAppClient,
    private val properties: GitHubAppProperties,
) {

    @JvmOverloads
    fun getRepositoryIssues(
        repoName: String,
        org: String? = null,
    ): List<GitHubIssueItemResponse> {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        return try {
            val issues = gitHubAppClient.executeInstallationRequest(
                method = "GET",
                path = "/repos/$targetOrg/$repoName/issues?state=all&sort=updated&direction=desc&per_page=100",
                responseType = Array<GitHubIssueItemResponse>::class.java,
            )
            // En GitHub la API de issues incluye las Pull Requests; filtramos las que tengan pull_request != null
            issues.filter { it.pullRequest == null }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun requiredOrganization(): String = properties.organization?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("github.app.organization must be configured with the GitHub Organization name")
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubIssueItemResponse(
    @JsonProperty("number") val number: Int = 0,
    @JsonProperty("title") val title: String = "",
    @JsonProperty("state") val state: String = "",
    @JsonProperty("html_url") val htmlUrl: String = "",
    @JsonProperty("user") val user: GitHubIssueUser? = null,
    @JsonProperty("comments") val comments: Int = 0,
    @JsonProperty("created_at") val createdAt: String = "",
    @JsonProperty("updated_at") val updatedAt: String = "",
    @JsonProperty("closed_at") val closedAt: String? = null,
    @JsonProperty("pull_request") val pullRequest: Any? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubIssueUser(
    @JsonProperty("login") val login: String = "",
)
