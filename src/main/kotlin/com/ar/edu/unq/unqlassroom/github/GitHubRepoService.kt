package com.ar.edu.unq.unqlassroom.github

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
    fun addCollaborator(
        repoName: String,
        username: String,
        permission: String = "push",
        org: String? = null,
    ) {
        val targetOrg = org?.takeIf { it.isNotBlank() } ?: requiredOrganization()
        val requestBody = objectMapper.writeValueAsString(
            AddCollaboratorRequest(permission = permission),
        )
        gitHubAppClient.executeInstallationRequest(
            method = "PUT",
            path = "/repos/$targetOrg/$repoName/collaborators/$username",
            responseType = Unit::class.java,
            body = requestBody,
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

    fun generarNombreRepo(curso: com.ar.edu.unq.unqlassroom.model.Curso, username: String): String =
        "${curso.generarNombreTeam()}_${username.trim().removerTildes()}"

    fun generarDescripcionRepo(curso: com.ar.edu.unq.unqlassroom.model.Curso, username: String): String =
        "Repositorio individual de ${username.trim()} para el curso ${curso.materia} - Año ${curso.anio} - Semestre ${curso.semestre} - Comisión ${curso.comision}"

    private fun requiredOrganization(): String = properties.organization.trim().takeIf { it.isNotEmpty() }
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
data class AddCollaboratorRequest(
    @JsonProperty("permission") val permission: String = "push",
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubRepoResponse(
    @JsonProperty("id") val id: Long = 0,
    @JsonProperty("name") val name: String = "",
    @JsonProperty("full_name") val fullName: String = "",
    @JsonProperty("html_url") val htmlUrl: String = "",
    @JsonProperty("clone_url") val cloneUrl: String = "",
)


