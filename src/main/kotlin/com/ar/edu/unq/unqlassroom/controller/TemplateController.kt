package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.dto.template.request.CrearTemplateRequestDTO
import com.ar.edu.unq.unqlassroom.dto.template.response.TemplateResponseDTO
import com.ar.edu.unq.unqlassroom.service.TemplateService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/templates")
class TemplateController(
    private val templateService: TemplateService,
) {

    @PostMapping
    fun crearTemplate(
        @RequestBody @Valid request: CrearTemplateRequestDTO,
        authentication: Authentication,
    ): ResponseEntity<TemplateResponseDTO> {
        val repo = templateService.crearTemplateRepository(
            name = request.name,
            description = request.description,
            solicitanteUsername = authentication.name,
        )
        val response = TemplateResponseDTO.desdeModelo(repo)
        return ResponseEntity.created(URI.create("/templates/${response.name}")).body(response)
    }

    @GetMapping
    fun listarTemplates(
        authentication: Authentication,
    ): ResponseEntity<List<TemplateResponseDTO>> {
        val templates = templateService.listarTemplates(authentication.name)
        val response = templates.map { TemplateResponseDTO.desdeModelo(it) }
        return ResponseEntity.ok(response)
    }
}
