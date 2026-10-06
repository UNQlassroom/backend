package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.dto.auth.request.GitHubLoginRequestDTO
import com.ar.edu.unq.unqlassroom.dto.auth.response.AuthResponseDTO
import org.springframework.security.core.userdetails.UserDetailsService

interface AuthService : UserDetailsService {
    fun loginConGitHub(dto: GitHubLoginRequestDTO): AuthResponseDTO
}
