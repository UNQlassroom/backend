package com.ar.edu.unq.unqlassroom.configuration

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class CorsConfig : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins("http://localhost:5173")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            .allowedHeaders(
                "Authorization",
                "Refresh-Token",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With"
            )
            .exposedHeaders("Authorization", "Refresh-Token")
            .allowCredentials(false)
            .maxAge(3600)
    }
}
