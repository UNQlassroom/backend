package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.ObtenerAlumnosResponseDTO
import com.ar.edu.unq.unqlassroom.service.CursoService
import jakarta.validation.Valid
import lombok.RequiredArgsConstructor
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin(origins = ["http://localhost:5173"])
@RequestMapping("/cursos")
@RequiredArgsConstructor
class CursoController (
    val cursoService: CursoService
)   {

    @PostMapping("/crear")
    fun crearCurso(@RequestBody @Valid cursoRequest: CursoRequestDTO
    ): ResponseEntity<CursoResponseDTO> {
        val response = cursoService.crearCurso(cursoRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/{id}/alumnos")
    fun agregarAlumnos(
        @PathVariable id: Long,
        @RequestBody @Valid request: AgregarAlumnosRequestDTO,
    ): ResponseEntity<AgregarAlumnosResponseDTO> {
        val response = cursoService.agregarAlumnos(id, request)
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun obtenerCursos(): ResponseEntity<List<CursoResponseDTO>> {
        val cursos = cursoService.obtenerCursos()
        return ResponseEntity.ok(cursos)
    }

    @GetMapping("/{id}/alumnos")
    fun obtenerAlumnos(@PathVariable id: Long): ResponseEntity<ObtenerAlumnosResponseDTO> {
        val alumnos = cursoService.obtenerAlumnos(id)
        return ResponseEntity.ok(alumnos)
    }
}