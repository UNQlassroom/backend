package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.Usuario
import org.springframework.security.core.userdetails.UserDetails

interface JwtService {
    fun generateToken(usuario: Usuario): String
    fun extractUsername(token: String): String
    fun extractRoles(token: String): List<String>
    fun isTokenValid(token: String, userDetails: UserDetails): Boolean
}
