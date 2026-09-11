package com.ar.edu.unq.unqlassroom.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
class CorsConfig {

    @Bean
    fun corsFilter(): CorsFilter {
        val source = UrlBasedCorsConfigurationSource()
        val config = CorsConfiguration()
        config.allowedOrigins = listOf("http://localhost:5173")
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        config.allowedHeaders = listOf(
            "Authorization",
            "Refresh-Token",
            "Content-Type",
            "Accept",
            "Origin",
            "X-Requested-With"
        )
        config.exposedHeaders = listOf("Authorization", "Refresh-Token")
        config.allowCredentials = false
        config.maxAge = 3600L
        source.registerCorsConfiguration("/**", config)
        return CorsFilter(source)
    }
}
