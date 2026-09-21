package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.service.impl.JwtServiceImpl
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Encoders
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.userdetails.User

class JwtServiceImplTest {

    private lateinit var jwtService: JwtServiceImpl
    private val mockSecretKey: String = Encoders.BASE64.encode(Jwts.SIG.HS256.key().build().encoded)
    private val expiration = 3600000L

    @BeforeEach
    fun setUp() {
        jwtService = JwtServiceImpl(
            jwtSecret = mockSecretKey,
            jwtExpiration = expiration
        )
    }

    @Test
    fun `generateToken generates valid token with correct username and role for alumno`() {
        val alumno = Usuario(id = 1L, username = "alumno1", esDocente = false)

        val token = jwtService.generateToken(alumno)

        assertNotNull(token)
        assertTrue(token.isNotBlank())
        assertEquals("alumno1", jwtService.extractUsername(token))

        val roles = jwtService.extractRoles(token)
        assertEquals(listOf("ROLE_ALUMNO"), roles)

        val userDetails = User.builder()
            .username("alumno1")
            .password("")
            .authorities("ROLE_ALUMNO")
            .build()
        assertTrue(jwtService.isTokenValid(token, userDetails))
    }

    @Test
    fun `generateToken generates valid token with correct role for docente`() {
        val docente = Usuario(id = 2L, username = "docente1", esDocente = true)

        val token = jwtService.generateToken(docente)

        assertNotNull(token)
        assertEquals("docente1", jwtService.extractUsername(token))

        val roles = jwtService.extractRoles(token)
        assertEquals(listOf("ROLE_DOCENTE"), roles)
    }

    @Test
    fun `isTokenValid returns false when username does not match userDetails`() {
        val alumno = Usuario(id = 1L, username = "alumno1", esDocente = false)
        val token = jwtService.generateToken(alumno)

        val otherUserDetails = User.builder()
            .username("otro_alumno")
            .password("")
            .authorities("ROLE_ALUMNO")
            .build()

        assertFalse(jwtService.isTokenValid(token, otherUserDetails))
    }

    @Test
    fun `isTokenValid returns false for expired token`() {
        val expiredJwtService = JwtServiceImpl(
            jwtSecret = mockSecretKey,
            jwtExpiration = -1000L
        )
        val alumno = Usuario(id = 1L, username = "alumno1", esDocente = false)
        val token = expiredJwtService.generateToken(alumno)

        val userDetails = User.builder()
            .username("alumno1")
            .password("")
            .authorities("ROLE_ALUMNO")
            .build()

        assertThrows(io.jsonwebtoken.ExpiredJwtException::class.java) {
            expiredJwtService.isTokenValid(token, userDetails)
        }
    }
}
