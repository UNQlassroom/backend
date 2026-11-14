package com.ar.edu.unq.unqlassroom.controller

import com.ar.edu.unq.unqlassroom.dto.usuario.request.ActualizarNombreRequestDTO
import com.ar.edu.unq.unqlassroom.dto.usuario.response.UsuarioResponseDTO
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/usuarios")
class UsuarioController(
    private val usuarioService: UsuarioService
) {

    @GetMapping("/me")
    fun obtenerUsuarioActual(authentication: Authentication): ResponseEntity<UsuarioResponseDTO> {
        val usuario = usuarioService.obtenerUsuario(authentication.name)
        return ResponseEntity.ok(UsuarioResponseDTO.desdeModelo(usuario))
    }

    @PutMapping("/me/nombre")
    fun actualizarNombreCompleto(
        authentication: Authentication,
        @RequestBody @Valid request: ActualizarNombreRequestDTO,
    ): ResponseEntity<UsuarioResponseDTO> {
        val usuarioActualizado = usuarioService.actualizarNombreCompleto(
            username = authentication.name,
            nombre = request.nombre,
            apellido = request.apellido,
        )
        return ResponseEntity.ok(UsuarioResponseDTO.desdeModelo(usuarioActualizado))
    }
}
