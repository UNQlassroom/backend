package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.controller.dtos.AuthResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.GitHubLoginRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.GitHubLoginResponseDTO
import com.ar.edu.unq.unqlassroom.service.AuthService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class AuthControllerTest {

    @Mock
    private lateinit var authService: AuthService

    @InjectMocks
    private lateinit var authController: AuthController

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build()
    }

    @Test
    fun `loginConGitHub returns 200 with token and user details`() {
        val requestDTO = GitHubLoginRequestDTO(code = "github-code-123", esDocente = false)
        val responseDTO = AuthResponseDTO(
            token = "jwt-test-token",
            user = GitHubLoginResponseDTO(
                id = 1L,
                username = "alumno1",
                esDocente = false,
                email = "alumno1@unq.edu.ar",
                nombreCompleto = "Alumno Uno"
            )
        )

        `when`(authService.loginConGitHub(requestDTO)).thenReturn(responseDTO)

        mockMvc.perform(
            post("/auth/github")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").value("jwt-test-token"))
            .andExpect(jsonPath("$.user.username").value("alumno1"))
            .andExpect(jsonPath("$.user.esDocente").value(false))
    }

    @Test
    fun `loginConGitHub returns 200 with redirectUrl when user requires joining org`() {
        val requestDTO = GitHubLoginRequestDTO(code = "github-code-org-required", esDocente = false)
        val responseDTO = AuthResponseDTO(
            token = null,
            user = null,
            requiereUnirseAOrg = true,
            redirectUrl = "https://github.com/orgs/UNQlassroom/invitation"
        )

        `when`(authService.loginConGitHub(requestDTO)).thenReturn(responseDTO)

        mockMvc.perform(
            post("/auth/github")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDTO))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.requiereUnirseAOrg").value(true))
            .andExpect(jsonPath("$.redirectUrl").value("https://github.com/orgs/UNQlassroom/invitation"))
            .andExpect(jsonPath("$.token").doesNotExist())
            .andExpect(jsonPath("$.user").doesNotExist())
    }
}
