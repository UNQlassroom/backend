package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.dto.auth.request.GitHubLoginRequestDTO
import com.ar.edu.unq.unqlassroom.dto.auth.response.AuthResponseDTO
import com.ar.edu.unq.unqlassroom.dto.auth.response.GitHubLoginResponseDTO
import com.ar.edu.unq.unqlassroom.integration.github.config.GitHubAppProperties
import com.ar.edu.unq.unqlassroom.integration.github.client.GitHubOAuthClient
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubOrgService
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.AuthService
import com.ar.edu.unq.unqlassroom.security.JwtService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class AuthServiceImpl(
    private val gitHubOAuthClient: GitHubOAuthClient,
    private val usuarioRepository: UsuarioRepository,
    private val jwtService: JwtService,
    private val gitHubOrgService: GitHubOrgService,
    private val properties: GitHubAppProperties,
) : AuthService {

    override fun loginConGitHub(dto: GitHubLoginRequestDTO): AuthResponseDTO {
        val accessToken = gitHubOAuthClient.intercambiarCodePorToken(dto.code)
        val perfil = gitHubOAuthClient.obtenerPerfilGitHub(accessToken)

        val usuarioExistente = usuarioRepository.findByUsername(perfil.login)
            ?: usuarioRepository.findByUsernameIgnoreCase(perfil.login)
        val usuario = usuarioExistente ?: Usuario(
            username = perfil.login,
            esDocente = dto.esDocente == true,
            email = perfil.email,
            nombreCompleto = perfil.name,
            githubState = "pending",
        )

        usuario.username = perfil.login
        perfil.email?.let { usuario.email = it }
        perfil.name?.let { usuario.nombreCompleto = it }
        usuario.esDocente = dto.esDocente == true

        val org = properties.organization?.trim().orEmpty()
        if (org.isNotEmpty() && usuario.githubState != "active") {
            if (usuarioExistente == null) {
                val rolOrg = if (dto.esDocente == true) "admin" else "member"
                val invitacion = gitHubOrgService.invitarMiembro(username = perfil.login, role = rolOrg)
                if (invitacion.state == "active") {
                    usuario.githubState = "active"
                }
            }

            if (usuario.githubState != "active") {
                usuarioRepository.save(usuario)
                val redirectUrl = "https://github.com/orgs/$org/invitation"
                return AuthResponseDTO(
                    token = null,
                    user = null,
                    requiereUnirseAOrg = true,
                    redirectUrl = redirectUrl,
                )
            }
        }

        val usuarioGuardado = usuarioRepository.save(usuario)
        val token = jwtService.generateToken(usuarioGuardado)

        return AuthResponseDTO(
            token = token,
            user = GitHubLoginResponseDTO.desdeModelo(usuarioGuardado)
        )
    }

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val usuario = usuarioRepository.findByUsername(username)
            ?: usuarioRepository.findByUsernameIgnoreCase(username)
            ?: throw UsernameNotFoundException("Usuario no encontrado: $username")

        val rol = if (usuario.esDocente) "ROLE_DOCENTE" else "ROLE_ALUMNO"
        return org.springframework.security.core.userdetails.User.builder()
            .username(usuario.username)
            .password("")
            .authorities(rol)
            .build()
    }
}
