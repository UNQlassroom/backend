package com.ar.edu.unq.unqlassroom.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        val config = CorsConfiguration().apply {
            allowedOrigins = listOf("http://localhost:5173")
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            allowedHeaders = listOf(
                "Authorization",
                "Refresh-Token",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With"
            )
            exposedHeaders = listOf("Authorization", "Refresh-Token")
            allowCredentials = false
            maxAge = 3600L
        }
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
