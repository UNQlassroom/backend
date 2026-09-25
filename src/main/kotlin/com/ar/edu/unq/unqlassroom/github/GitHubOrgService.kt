package com.ar.edu.unq.unqlassroom.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

@Service
class GitHubOrgService(
    private val gitHubAppClient: GitHubAppClient,
    private val properties: GitHubAppProperties,
    private val objectMapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
) {

    @JvmOverloads
    fun invitarMiembro(
        username: String,
        role: String = "member",
        org: String? = null,
    ): GitHubOrgMembershipResponse {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val requestBody = objectMapper.writeValueAsString(
            SetOrgMembershipRequest(role = role)
        )
        return try {
            gitHubAppClient.executeInstallationRequest(
                method = "PUT",
                path = "/orgs/$targetOrg/memberships/$username",
                responseType = GitHubOrgMembershipResponse::class.java,
                body = requestBody,
            )
        } catch (_: Exception) {
            GitHubOrgMembershipResponse(
                state = "pending",
                role = role,
            )
        }
    }

    @JvmOverloads
    fun obtenerMembresia(
        username: String,
        org: String? = null,
    ): GitHubOrgMembershipResponse? {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        return try {
            gitHubAppClient.executeInstallationRequest(
                method = "GET",
                path = "/orgs/$targetOrg/memberships/$username",
                responseType = GitHubOrgMembershipResponse::class.java,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun requiredOrganization(): String = properties.organization?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("github.app.organization must be configured with the GitHub Organization name")
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SetOrgMembershipRequest(
    @JsonProperty("role") val role: String = "member",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubOrgMembershipResponse(
    @JsonProperty("state") val state: String = "pending",
    @JsonProperty("role") val role: String = "member",
)
