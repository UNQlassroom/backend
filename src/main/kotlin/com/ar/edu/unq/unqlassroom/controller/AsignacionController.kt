package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.dto.asignacion.request.*
import com.ar.edu.unq.unqlassroom.dto.asignacion.response.*
import com.ar.edu.unq.unqlassroom.service.AsignacionService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.net.URI

@RestController
@RequestMapping("/cursos/{cursoId}/asignaciones")
class AsignacionController(
    private val asignacionService: AsignacionService
) {

    @PostMapping
    fun crearAsignacion(
        @PathVariable cursoId: Long,
        @RequestBody @Valid request: CrearAsignacionRequestDTO,
        authentication: Authentication,
    ): ResponseEntity<AsignacionResponseDTO> {
        val asignacion = request.aModelo()
        val asignacionGuardada = asignacionService.crearAsignacion(cursoId, asignacion, authentication.name)
        val response = AsignacionResponseDTO.desdeModelo(asignacionGuardada)
        return ResponseEntity.created(URI.create("/cursos/$cursoId/asignaciones/${response.id}")).body(response)
    }

    @GetMapping
    fun obtenerAsignaciones(
        @PathVariable cursoId: Long,
        authentication: Authentication,
    ): ResponseEntity<List<AsignacionResponseDTO>> {
        val asignaciones = asignacionService.obtenerAsignaciones(cursoId, authentication.name)
        return ResponseEntity.ok(asignaciones.map { AsignacionResponseDTO.desdeModelo(it) })
    }

    @GetMapping("/{asignacionId}")
    fun obtenerAsignacion(
        @PathVariable cursoId: Long,
        @PathVariable asignacionId: Long,
        authentication: Authentication,
    ): ResponseEntity<AsignacionResponseDTO> {
        val asignacion = asignacionService.obtenerAsignacion(cursoId, asignacionId, authentication.name)
        return ResponseEntity.ok(AsignacionResponseDTO.desdeModelo(asignacion))
    }

    @PostMapping("/{asignacionId}/entregar")
    fun marcarAsignacionComoEntregada(
        @PathVariable cursoId: Long,
        @PathVariable asignacionId: Long,
        @RequestParam(required = true) grupo: Long,
        authentication: Authentication,
    ): ResponseEntity<AsignacionResponseDTO> {
        val asignacion = asignacionService.marcarAsignacionComoEntregada(
            cursoId = cursoId,
            asignacionId = asignacionId,
            solicitanteUsername = authentication.name,
            grupoId = grupo,
        )
        return ResponseEntity.ok(AsignacionResponseDTO.desdeModelo(asignacion))
    }

    @RequestMapping(
        value = ["/{asignacionId}/calificar"],
        method = [RequestMethod.POST, RequestMethod.PUT]
    )
    fun calificarAsignacion(
        @PathVariable cursoId: Long,
        @PathVariable asignacionId: Long,
        @RequestBody @Valid request: CalificarAsignacionRequestDTO,
        authentication: Authentication,
    ): ResponseEntity<AsignacionResponseDTO> {
        val asignacion = asignacionService.calificarAsignacion(
            cursoId = cursoId,
            asignacionId = asignacionId,
            grupoId = request.grupoId,
            calificacion = request.calificacion,
            observaciones = request.observaciones,
            solicitanteUsername = authentication.name,
        )
        return ResponseEntity.ok(AsignacionResponseDTO.desdeModelo(asignacion))
    }

    @GetMapping("/{asignacionId}/correcciones")
    fun obtenerCorrecciones(
        @PathVariable cursoId: Long,
        @PathVariable asignacionId: Long,
        authentication: Authentication,
    ): ResponseEntity<List<CorreccionGrupoResponseDTO>> {
        val response = asignacionService.obtenerCorrecciones(cursoId, asignacionId, authentication.name)
        return ResponseEntity.ok(response)
    }
}
