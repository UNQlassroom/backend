package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.AuthResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.GitHubLoginRequestDTO
import org.springframework.security.core.userdetails.UserDetailsService

interface AuthService : UserDetailsService {
    fun loginConGitHub(dto: GitHubLoginRequestDTO): AuthResponseDTO
}
