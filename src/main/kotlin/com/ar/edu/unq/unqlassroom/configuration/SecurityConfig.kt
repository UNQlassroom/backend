package com.ar.edu.unq.unqlassroom.configuration

import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .exceptionHandling {
                it.authenticationEntryPoint { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "No autorizado")
                }
                it.accessDeniedHandler { _, response, _ ->
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Acceso denegado: se requieren permisos de docente")
                }
            }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                auth.requestMatchers("/auth/**", "/error").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/cursos/crear").hasRole("DOCENTE")
                auth.requestMatchers(HttpMethod.POST, "/cursos/*/alumnos").hasRole("DOCENTE")
                auth.requestMatchers(HttpMethod.POST, "/cursos/*/alumnos/sync").hasRole("DOCENTE")
                auth.requestMatchers(HttpMethod.POST, "/cursos/*/asignaciones/*/entregar").authenticated()
                auth.requestMatchers(HttpMethod.POST, "/cursos/*/asignaciones").hasRole("DOCENTE")
                auth.requestMatchers(HttpMethod.POST, "/templates").hasRole("DOCENTE")
                auth.requestMatchers(HttpMethod.GET, "/templates").hasRole("DOCENTE")
                auth.requestMatchers("/cursos/**").authenticated()
                auth.anyRequest().authenticated()
            }
            .sessionManagement {
                it.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }
}
