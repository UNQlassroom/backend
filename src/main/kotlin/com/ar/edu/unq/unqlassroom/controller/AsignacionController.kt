package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.controller.dtos.*
import com.ar.edu.unq.unqlassroom.service.AsignacionService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
class AsignacionController(
    private val asignacionService: AsignacionService
) {

    @PostMapping("/cursos/{cursoId}/asignaciones")
    fun crearAsignacion(
        @PathVariable cursoId: Long,
        @RequestBody @Valid request: CrearAsignacionRequestDTO,
        authentication: Authentication,
    ): ResponseEntity<AsignacionResponseDTO> {
        val response = asignacionService.crearAsignacion(cursoId, request, authentication.name)
        return ResponseEntity.created(URI.create("/cursos/$cursoId/asignaciones/${response.id}")).body(response)
    }

    @GetMapping("/cursos/{cursoId}/asignaciones")
    fun obtenerAsignaciones(
        @PathVariable cursoId: Long,
        authentication: Authentication,
    ): ResponseEntity<List<AsignacionResponseDTO>> {
        val response = asignacionService.obtenerAsignaciones(cursoId, authentication.name)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/cursos/{cursoId}/asignaciones/{asignacionId}")
    fun obtenerAsignacion(
        @PathVariable cursoId: Long,
        @PathVariable asignacionId: Long,
        authentication: Authentication,
    ): ResponseEntity<AsignacionResponseDTO> {
        val response = asignacionService.obtenerAsignacion(cursoId, asignacionId, authentication.name)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/templates")
    fun crearTemplate(
        @RequestBody @Valid request: CrearTemplateRepoRequestDTO,
        authentication: Authentication,
    ): ResponseEntity<TemplateRepoResponseDTO> {
        val response = asignacionService.crearTemplateRepository(request, authentication.name)
        return ResponseEntity.created(URI.create("/templates/${response.name}")).body(response)
    }

    @GetMapping("/templates")
    fun listarTemplates(
        authentication: Authentication,
    ): ResponseEntity<List<TemplateRepoResponseDTO>> {
        val response = asignacionService.listarTemplates(authentication.name)
        return ResponseEntity.ok(response)
    }
}
