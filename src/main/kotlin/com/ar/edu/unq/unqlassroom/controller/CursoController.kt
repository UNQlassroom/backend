package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoResponseDTO
import com.ar.edu.unq.unqlassroom.service.CursoService
import jakarta.validation.Valid
import lombok.RequiredArgsConstructor
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
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



}