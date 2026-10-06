package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.dto.curso.request.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.dto.curso.request.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.dto.curso.response.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.dto.curso.response.CursoAlumnosResponseDTO
import com.ar.edu.unq.unqlassroom.service.CursoService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI

@RestController
@RequestMapping("/cursos")
class CursoController(
    private val cursoService: CursoService
) {

    @PostMapping
    fun crearCurso(
        @RequestBody @Valid cursoRequest: CursoRequestDTO,
        authentication: Authentication
    ): ResponseEntity<CursoResponseDTO> {
        val curso = cursoRequest.aModelo()
        val cursoGuardado = cursoService.crearCurso(curso, authentication.name)
        val response = CursoResponseDTO.desdeModelo(cursoGuardado)
        return ResponseEntity.created(URI.create("/cursos/${response.id}")).body(response)
    }

    @PostMapping("/{id}/alumnos")
    fun agregarAlumnos(
        @PathVariable id: Long,
        @RequestBody @Valid request: AgregarAlumnosRequestDTO,
        authentication: Authentication,
    ): ResponseEntity<CursoAlumnosResponseDTO> {
        val inscripciones = cursoService.agregarAlumnos(id, request.usernames, authentication.name)
        val response = CursoAlumnosResponseDTO.desdeModelo(id, inscripciones)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{id}/alumnos/sync")
    fun sincronizarAlumnos(
        @PathVariable id: Long,
        authentication: Authentication,
    ): ResponseEntity<CursoAlumnosResponseDTO> {
        val inscripciones = cursoService.sincronizarAlumnos(id, authentication.name)
        val response = CursoAlumnosResponseDTO.desdeModelo(id, inscripciones)
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun obtenerCursos(
        authentication: Authentication,
    ): ResponseEntity<List<CursoResponseDTO>> {
        val esDocente = authentication.authorities.any { it.authority == "ROLE_DOCENTE" }
        val cursos = cursoService.obtenerCursos(authentication.name, esDocente)
        return ResponseEntity.ok(cursos.map { CursoResponseDTO.desdeModelo(it) })
    }

    @GetMapping("/{id}")
    fun obtenerCurso(
        @PathVariable id: Long,
        authentication: Authentication,
    ): ResponseEntity<CursoResponseDTO> {
        val curso = cursoService.obtenerCurso(id, authentication.name)
        return ResponseEntity.ok(CursoResponseDTO.desdeModelo(curso))
    }

    @GetMapping("/{id}/alumnos")
    fun obtenerAlumnos(
        @PathVariable id: Long,
        authentication: Authentication,
    ): ResponseEntity<CursoAlumnosResponseDTO> {
        val inscripciones = cursoService.obtenerAlumnos(id, authentication.name)
        val response = CursoAlumnosResponseDTO.desdeModelo(id, inscripciones)
        return ResponseEntity.ok(response)
    }
}
