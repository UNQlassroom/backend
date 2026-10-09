package com.ar.edu.unq.unqlassroom.security

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.servlet.HandlerExceptionResolver

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    @Qualifier("handlerExceptionResolver")
    private val resolver: HandlerExceptionResolver,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .exceptionHandling {
                it.authenticationEntryPoint { request, response, authException ->
                    resolver.resolveException(request, response, null, authException)
                }
                it.accessDeniedHandler { request, response, accessDeniedException ->
                    resolver.resolveException(request, response, null, accessDeniedException)
                }
            }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                auth.requestMatchers("/auth/**", "/error").permitAll()
                auth.requestMatchers(HttpMethod.POST, "/cursos").hasRole("DOCENTE")
                auth.requestMatchers(HttpMethod.POST, "/cursos/*/alumnos").hasRole("DOCENTE")
                auth.requestMatchers(HttpMethod.POST, "/cursos/*/alumnos/sync").hasRole("DOCENTE")
                auth.requestMatchers(HttpMethod.POST, "/cursos/*/asignaciones/*/entregar").authenticated()
                auth.requestMatchers(HttpMethod.POST, "/cursos/*/asignaciones/*/calificar").hasRole("DOCENTE")
                auth.requestMatchers(HttpMethod.PUT, "/cursos/*/asignaciones/*/calificar").hasRole("DOCENTE")
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
