package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.dto.template.request.CrearTemplateRequestDTO
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.service.TemplateService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class TemplateControllerTest {

    @Mock
    private lateinit var templateService: TemplateService

    @InjectMocks
    private lateinit var templateController: TemplateController

    private lateinit var mockMvc: MockMvc
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(templateController).build()
    }

    @Test
    fun `crearTemplate returns 201 and created template`() {
        val request = CrearTemplateRequestDTO(name = "template-tp3", description = "Desc")
        val repo = Repositorio(
            id = 1L,
            nombre = "template-tp3",
            htmlUrl = "https://github.com/unqlassroom/template-tp3",
            esTemplate = true,
            descripcion = "Desc"
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(templateService.crearTemplateRepository("template-tp3", "Desc", "profe")).thenReturn(repo)

        mockMvc.perform(
            post("/templates")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/templates/template-tp3"))
            .andExpect(jsonPath("$.name").value("template-tp3"))
            .andExpect(jsonPath("$.fullName").value("template-tp3"))
            .andExpect(jsonPath("$.htmlUrl").value("https://github.com/unqlassroom/template-tp3"))
            .andExpect(jsonPath("$.description").value("Desc"))
    }

    @Test
    fun `crearTemplate returns 400 when name is blank`() {
        val request = CrearTemplateRequestDTO(name = "   ", description = "Desc")
        val auth = UsernamePasswordAuthenticationToken("profe", null)

        mockMvc.perform(
            post("/templates")
                .principal(auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `listarTemplates returns 200 and list`() {
        val repo = Repositorio(
            id = 1L,
            nombre = "template-tp3",
            htmlUrl = "https://github.com/unqlassroom/template-tp3",
            esTemplate = true,
            descripcion = "Desc"
        )

        val auth = UsernamePasswordAuthenticationToken("profe", null)
        `when`(templateService.listarTemplates("profe")).thenReturn(listOf(repo))

        mockMvc.perform(
            get("/templates")
                .principal(auth)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.size()").value(1))
            .andExpect(jsonPath("$[0].name").value("template-tp3"))
            .andExpect(jsonPath("$[0].fullName").value("template-tp3"))
            .andExpect(jsonPath("$[0].htmlUrl").value("https://github.com/unqlassroom/template-tp3"))
            .andExpect(jsonPath("$[0].description").value("Desc"))
    }
}
