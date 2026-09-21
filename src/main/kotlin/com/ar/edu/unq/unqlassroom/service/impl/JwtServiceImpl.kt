package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.service.JwtService
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtServiceImpl(
    @Value("\${unqlassroom.jwt.secret}")
    private val jwtSecret: String,

    @Value("\${unqlassroom.jwt.expiration}")
    private val jwtExpiration: Long
) : JwtService {

    override fun generateToken(usuario: Usuario): String {
        val rol = if (usuario.esDocente) "ROLE_DOCENTE" else "ROLE_ALUMNO"
        val claims = mapOf(
            "roles" to listOf(rol),
            "esDocente" to usuario.esDocente
        )

        val now = System.currentTimeMillis()
        return Jwts.builder()
            .claims(claims)
            .subject(usuario.username)
            .issuedAt(Date(now))
            .expiration(Date(now + jwtExpiration))
            .signWith(getSigningKey())
            .compact()
    }

    override fun extractUsername(token: String): String {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .payload
            .subject
    }

    @Suppress("UNCHECKED_CAST")
    override fun extractRoles(token: String): List<String> {
        val payload = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .payload
        return payload["roles"] as? List<String> ?: emptyList()
    }

    override fun isTokenValid(token: String, userDetails: UserDetails): Boolean {
        val username = extractUsername(token)
        return username == userDetails.username && !isTokenExpired(token)
    }

    private fun isTokenExpired(token: String): Boolean {
        val expiration = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .payload
            .expiration
        return expiration.before(Date())
    }

    private fun getSigningKey(): SecretKey {
        val keyBytes = Decoders.BASE64.decode(jwtSecret)
        return Keys.hmacShaKeyFor(keyBytes)
    }
}
