package com.ar.edu.unq.unqlassroom.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service

@Service
class GitHubCollaboratorService(
    private val gitHubAppClient: GitHubAppClient,
    private val properties: GitHubAppProperties,
    private val objectMapper: ObjectMapper = ObjectMapper()
        .findAndRegisterModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
) {

    @JvmOverloads
    fun addCollaborator(
        repoName: String,
        username: String,
        permission: String = "push", // TODO aca deberia ser "pull" si al alumno es invitado al repo main del curso
        org: String? = null,
    ): GitHubCollaboratorResponse {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val requestBody = objectMapper.writeValueAsString(
            AddCollaboratorRequest(permission = permission),
        )
        val invitation: GitHubRepoInvitationItemResponse? = gitHubAppClient.executeInstallationRequest(
            method = "PUT",
            path = "/repos/$targetOrg/$repoName/collaborators/$username",
            responseType = GitHubRepoInvitationItemResponse::class.java,
            body = requestBody,
        )
        val state = if (invitation != null) "pending" else "active"
        return GitHubCollaboratorResponse(
            username = username,
            role = permission,
            state = state,
        )
    }

    @JvmOverloads
    fun getCollaborators(
        repoName: String,
        org: String? = null,
    ): List<GitHubCollaboratorItemResponse> {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        return try {
            gitHubAppClient.executeInstallationRequest(
                method = "GET",
                path = "/repos/$targetOrg/$repoName/collaborators?affiliation=direct&per_page=100",
                responseType = Array<GitHubCollaboratorItemResponse>::class.java,
            ).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @JvmOverloads
    fun getInvitations(
        repoName: String,
        org: String? = null,
    ): List<GitHubRepoInvitationItemResponse> {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        return try {
            gitHubAppClient.executeInstallationRequest(
                method = "GET",
                path = "/repos/$targetOrg/$repoName/invitations?per_page=100",
                responseType = Array<GitHubRepoInvitationItemResponse>::class.java,
            ).toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @JvmOverloads
    fun getRepoMembers(
        repoName: String,
        org: String? = null,
    ): List<GitHubRepoMemberResponse> {
        val collaborators = getCollaborators(repoName, org).map {
            GitHubRepoMemberResponse(
                username = it.login,
                role = it.roleName ?: "member",
                state = "active",
            )
        }
        val invitations = getInvitations(repoName, org).mapNotNull { invitation ->
            invitation.invitee?.login?.takeIf { it.isNotBlank() }?.let { login ->
                GitHubRepoMemberResponse(
                    username = login,
                    role = invitation.permissions ?: "member",
                    state = "pending",
                )
            }
        }
        return collaborators + invitations
    }

    private fun requiredOrganization(): String = properties.organization?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalStateException("github.app.organization must be configured with the GitHub Organization name")
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AddCollaboratorRequest(
    @JsonProperty("permission") val permission: String = "push",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubCollaboratorItemResponse(
    @JsonProperty("login") val login: String = "",
    @JsonProperty("role_name") val roleName: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubRepoInvitationItemResponse(
    @JsonProperty("id") val id: Long = 0,
    @JsonProperty("invitee") val invitee: GitHubInviteeResponse? = null,
    @JsonProperty("permissions") val permissions: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubInviteeResponse(
    @JsonProperty("login") val login: String = "",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubCollaboratorResponse(
    val username: String = "",
    val role: String = "member",
    val state: String = "active",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubRepoMemberResponse(
    val username: String = "",
    val role: String = "member",
    val state: String = "active",
)
