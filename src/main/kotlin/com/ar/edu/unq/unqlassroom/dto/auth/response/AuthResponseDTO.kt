package com.ar.edu.unq.unqlassroom.dto.auth.response

data class AuthResponseDTO(
    val token: String? = null,
    val user: GitHubLoginResponseDTO? = null,
    val requiereUnirseAOrg: Boolean = false,
    val redirectUrl: String? = null,
)
