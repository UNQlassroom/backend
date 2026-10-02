package com.ar.edu.unq.unqlassroom.security

import io.jsonwebtoken.JwtException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService

@ExtendWith(MockitoExtension::class)
class JwtAuthenticationFilterTest {

    @Mock
    private lateinit var jwtService: JwtService

    @Mock
    private lateinit var userDetailsService: UserDetailsService

    private lateinit var filter: JwtAuthenticationFilter

    @BeforeEach
    fun setUp() {
        SecurityContextHolder.clearContext()
        filter = JwtAuthenticationFilter(jwtService, userDetailsService)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `doFilterInternal continues without auth when header is null or does not start with Bearer`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val filterChain = MockFilterChain()

        filter.doFilter(request, response, filterChain)
        assertNull(SecurityContextHolder.getContext().authentication)

        val request2 = MockHttpServletRequest()
        request2.addHeader("Authorization", "Basic abc1234")
        val filterChain2 = MockFilterChain()
        filter.doFilter(request2, response, filterChain2)
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `doFilterInternal catches JwtException and continues filter chain`() {
        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer invalid-jwt")
        val response = MockHttpServletResponse()
        val filterChain = MockFilterChain()

        `when`(jwtService.extractUsername("invalid-jwt")).thenThrow(object : JwtException("Expired token") {})

        filter.doFilter(request, response, filterChain)

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `doFilterInternal catches IllegalArgumentException and continues filter chain`() {
        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer malformed-jwt")
        val response = MockHttpServletResponse()
        val filterChain = MockFilterChain()

        `when`(jwtService.extractUsername("malformed-jwt")).thenThrow(IllegalArgumentException("Malformed token"))

        filter.doFilter(request, response, filterChain)

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `doFilterInternal does not authenticate when token is invalid`() {
        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer valid-format-jwt")
        val response = MockHttpServletResponse()
        val filterChain = MockFilterChain()

        val userDetails = User("alumno1", "pass", listOf(SimpleGrantedAuthority("ROLE_USER")))
        `when`(jwtService.extractUsername("valid-format-jwt")).thenReturn("alumno1")
        `when`(userDetailsService.loadUserByUsername("alumno1")).thenReturn(userDetails)
        `when`(jwtService.isTokenValid("valid-format-jwt", userDetails)).thenReturn(false)

        filter.doFilter(request, response, filterChain)

        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `doFilterInternal sets authentication when token is valid`() {
        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer valid-jwt")
        val response = MockHttpServletResponse()
        val filterChain = MockFilterChain()

        val userDetails = User("docente1", "pass", listOf(SimpleGrantedAuthority("ROLE_DOCENTE")))
        `when`(jwtService.extractUsername("valid-jwt")).thenReturn("docente1")
        `when`(userDetailsService.loadUserByUsername("docente1")).thenReturn(userDetails)
        `when`(jwtService.isTokenValid("valid-jwt", userDetails)).thenReturn(true)

        filter.doFilter(request, response, filterChain)

        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        assertEquals("docente1", auth?.name)
    }

    @Test
    fun `doFilterInternal does not re-authenticate when context already has authentication`() {
        val request = MockHttpServletRequest()
        request.addHeader("Authorization", "Bearer valid-jwt")
        val response = MockHttpServletResponse()
        val filterChain = MockFilterChain()

        val existingAuth = UsernamePasswordAuthenticationToken("existingUser", null, emptyList())
        SecurityContextHolder.getContext().authentication = existingAuth

        `when`(jwtService.extractUsername("valid-jwt")).thenReturn("newUser")

        filter.doFilter(request, response, filterChain)

        assertEquals("existingUser", SecurityContextHolder.getContext().authentication?.name)
        verifyNoInteractions(userDetailsService)
    }
}
