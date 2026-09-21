package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AlumnosDeUnCursoResponseDTO
import com.ar.edu.unq.unqlassroom.service.CursoService
import jakarta.validation.Valid
import lombok.RequiredArgsConstructor
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
@RequiredArgsConstructor
class CursoController (
    val cursoService: CursoService
)   {

    @PostMapping("/crear")
    fun crearCurso(
        @RequestBody @Valid cursoRequest: CursoRequestDTO,
        authentication: Authentication
    ): ResponseEntity<CursoResponseDTO> {
        val response = cursoService.crearCurso(cursoRequest, authentication.name)
        return ResponseEntity.created(URI.create("/cursos" + response.id)).body(response)
    }

    @PostMapping("/{id}/alumnos")
    fun agregarAlumnos(
        @PathVariable id: Long,
        @RequestBody @Valid request: AgregarAlumnosRequestDTO,
    ): ResponseEntity<AlumnosDeUnCursoResponseDTO> {
        val response = cursoService.agregarAlumnos(id, request)
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun obtenerCursos(): ResponseEntity<List<CursoResponseDTO>> {
        val cursos = cursoService.obtenerCursos()
        return ResponseEntity.ok(cursos)
    }

    @GetMapping("/{id}/alumnos")
    fun obtenerAlumnos(@PathVariable id: Long): ResponseEntity<AlumnosDeUnCursoResponseDTO> {
        val alumnos = cursoService.obtenerAlumnos(id)
        return ResponseEntity.ok(alumnos)
    }
}