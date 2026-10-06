package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.dto.auth.AuthResponseDTO
import com.ar.edu.unq.unqlassroom.dto.auth.GitHubLoginRequestDTO
import org.springframework.security.core.userdetails.UserDetailsService

interface AuthService : UserDetailsService {
    fun loginConGitHub(dto: GitHubLoginRequestDTO): AuthResponseDTO
}
