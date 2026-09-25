package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.controller.dtos.AuthResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.GitHubLoginRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.GitHubLoginResponseDTO
import com.ar.edu.unq.unqlassroom.github.GitHubAppProperties
import com.ar.edu.unq.unqlassroom.github.GitHubOAuthClient
import com.ar.edu.unq.unqlassroom.github.GitHubOrgService
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.AuthService
import com.ar.edu.unq.unqlassroom.service.JwtService
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

        val org = properties.organization?.trim().orEmpty()
        if (org.isNotEmpty()) {
            val membresia = gitHubOrgService.obtenerMembresia(perfil.login)
            val esMiembroActivo = membresia != null && membresia.state == "active"

            if (!esMiembroActivo) {
                if (membresia == null) {
                    val rolOrg = if (dto.esDocente == true) "admin" else "member"
                    gitHubOrgService.invitarMiembro(username = perfil.login, role = rolOrg)
                }
                val redirectUrl = "https://github.com/orgs/$org/invitation"
                return AuthResponseDTO(
                    token = null,
                    user = null,
                    requiereUnirseAOrg = true,
                    redirectUrl = redirectUrl,
                )
            }
        }

        val usuarioExistente = usuarioRepository.findByUsername(perfil.login)
        val usuarioAGuardar = if (usuarioExistente != null) {
            perfil.email?.let { usuarioExistente.email = it }
            perfil.name?.let { usuarioExistente.nombreCompleto = it }
            usuarioExistente.esDocente = dto.esDocente == true
            usuarioExistente
        } else {
            Usuario(
                username = perfil.login,
                esDocente = dto.esDocente == true,
                email = perfil.email,
                nombreCompleto = perfil.name
            )
        }

        val usuarioGuardado = usuarioRepository.save(usuarioAGuardar)
        val token = jwtService.generateToken(usuarioGuardado)

        return AuthResponseDTO(
            token = token,
            user = GitHubLoginResponseDTO.desdeModelo(usuarioGuardado)
        )
    }

    @Transactional(readOnly = true)
    override fun loadUserByUsername(username: String): UserDetails {
        val usuario = usuarioRepository.findByUsername(username)
            ?: throw UsernameNotFoundException("Usuario no encontrado: $username")

        val rol = if (usuario.esDocente) "ROLE_DOCENTE" else "ROLE_ALUMNO"
        return org.springframework.security.core.userdetails.User.builder()
            .username(usuario.username)
            .password("")
            .authorities(rol)
            .build()
    }
}
