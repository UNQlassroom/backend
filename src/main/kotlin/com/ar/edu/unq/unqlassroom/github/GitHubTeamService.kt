package com.ar.edu.unq.unqlassroom.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

@Service
class GitHubTeamService(
    private val gitHubAppClient: GitHubAppClient,
    private val properties: GitHubAppProperties,
    private val objectMapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
) {

    @JvmOverloads
    fun createTeam(
        name: String,
        description: String? = null,
        privacy: String = "closed",
        org: String? = null,
    ): GitHubTeamResponse {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val requestBody = objectMapper.writeValueAsString(
            CreateTeamRequest(
                name = name,
                description = description,
                privacy = privacy,
            ),
        )
        return gitHubAppClient.executeInstallationRequest(
            method = "POST",
            path = "/orgs/$targetOrg/teams",
            responseType = GitHubTeamResponse::class.java,
            body = requestBody,
        )
    }

    private fun requiredOrganization(): String = properties.organization.trim().takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("github.app.organization must be configured with the GitHub Organization name")
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class CreateTeamRequest(
    @JsonProperty("name") val name: String,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("privacy") val privacy: String = "closed",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubTeamResponse(
    @JsonProperty("id") val id: Long = 0,
    @JsonProperty("node_id") val nodeId: String = "",
    @JsonProperty("name") val name: String = "",
    @JsonProperty("slug") val slug: String = "",
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("privacy") val privacy: String? = null,
    @JsonProperty("html_url") val htmlUrl: String? = null,
)
