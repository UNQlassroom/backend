package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class MembershipWebhookHandlerTest {

    @Mock
    private lateinit var usuarioRepository: UsuarioRepository

    private val objectMapper = ObjectMapper()
    private lateinit var handler: MembershipWebhookHandler

    @BeforeEach
    fun setUp() {
        handler = MembershipWebhookHandler(usuarioRepository, objectMapper)
    }

    @Test
    fun `canHandle retorna true solo para eventos organization con action member_added case-insensitive`() {
        val eventOrgAdded = WebhookEvent(eventType = "organization", action = "member_added", payload = "{}")
        val eventOrgUpper = WebhookEvent(eventType = "ORGANIZATION", action = "MEMBER_ADDED", payload = "{}")
        val eventFromPayload = WebhookEvent(eventType = "organization", payload = """{"action": "member_added"}""")
        val eventOrgInvited = WebhookEvent(eventType = "organization", action = "member_invited", payload = "{}")
        val eventMembership = WebhookEvent(eventType = "membership", action = "member_added", payload = "{}")
        val eventPush = WebhookEvent(eventType = "push", action = "member_added", payload = "{}")

        assertTrue(handler.canHandle(eventOrgAdded))
        assertTrue(handler.canHandle(eventOrgUpper))
        assertTrue(handler.canHandle(eventFromPayload))
        assertFalse(handler.canHandle(eventOrgInvited))
        assertFalse(handler.canHandle(eventMembership))
        assertFalse(handler.canHandle(eventPush))
    }

    @Test
    fun `handle con organization member_added activa githubState del usuario`() {
        val payload = """
        {
          "action": "member_added",
          "membership": {
            "state": "active",
            "role": "member",
            "user": {
              "login": "thiagoDePrueba"
            }
          },
          "organization": {
            "login": "UNQlassroom-cursos"
          }
        }
        """.trimIndent()

        val event = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "organization",
            action = "member_added",
            payload = payload
        )

        val usuario = Usuario(id = 1L, username = "thiagoDePrueba", githubState = "pending")

        `when`(usuarioRepository.findByUsernameIgnoreCase("thiagoDePrueba")).thenReturn(usuario)

        handler.handle(event)

        assertEquals("active", usuario.githubState)
        verify(usuarioRepository).save(usuario)
    }

    @Test
    fun `handle con organization member_added actualiza base de datos cuando docente guardo username con mayusculas diferentes`() {
        val payload = """
        {
          "action": "member_added",
          "membership": {
            "state": "active",
            "role": "member",
            "user": {
              "login": "thiagoDePrueba"
            }
          },
          "organization": {
            "login": "UNQlassroom-cursos"
          }
        }
        """.trimIndent()

        val event = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "organization",
            action = "member_added",
            payload = payload
        )

        // El usuario fue guardado previamente en mayúsculas ("THIAGODEPRUEBA")
        val usuario = Usuario(id = 1L, username = "THIAGODEPRUEBA", githubState = "pending")

        `when`(usuarioRepository.findByUsernameIgnoreCase("thiagoDePrueba")).thenReturn(usuario)

        handler.handle(event)

        assertEquals("active", usuario.githubState)
        assertEquals("thiagoDePrueba", usuario.username)
        verify(usuarioRepository).save(usuario)
    }

    @Test
    fun `handle cuando el usuario no existe en la base de datos no persiste ni falla`() {
        val payload = """
        {
          "action": "member_added",
          "membership": {
            "user": {
              "login": "desconocido"
            }
          }
        }
        """.trimIndent()

        val event = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "organization",
            action = "member_added",
            payload = payload
        )

        `when`(usuarioRepository.findByUsernameIgnoreCase("desconocido")).thenReturn(null)

        handler.handle(event)

        verify(usuarioRepository).findByUsernameIgnoreCase("desconocido")
        verify(usuarioRepository, never()).save(any())
    }

    @Test
    fun `handle cuando el usuario ya estaba active no vuelve a persistir`() {
        val payload = """
        {
          "action": "member_added",
          "membership": {
            "user": {
              "login": "alumnoActivo"
            }
          }
        }
        """.trimIndent()

        val event = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "organization",
            action = "member_added",
            payload = payload
        )

        val usuario = Usuario(id = 3L, username = "alumnoActivo", githubState = "active")
        `when`(usuarioRepository.findByUsernameIgnoreCase("alumnoActivo")).thenReturn(usuario)

        handler.handle(event)

        verify(usuarioRepository).findByUsernameIgnoreCase("alumnoActivo")
        verify(usuarioRepository, never()).save(any())
    }

    @Test
    fun `handle extrae username desde nodo member si membership user no esta presente`() {
        val payload = """
        {
          "action": "member_added",
          "member": {
            "login": "thiagoDePrueba"
          }
        }
        """.trimIndent()

        val event = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "organization",
            action = "member_added",
            payload = payload
        )

        val usuario = Usuario(id = 1L, username = "THIAGODEPRUEBA", githubState = "pending")
        `when`(usuarioRepository.findByUsernameIgnoreCase("thiagoDePrueba")).thenReturn(usuario)

        handler.handle(event)

        assertEquals("active", usuario.githubState)
        assertEquals("thiagoDePrueba", usuario.username)
        verify(usuarioRepository).save(usuario)
    }

    @Test
    fun `handle cuando no se puede extraer username no interactua con repositorio`() {
        val payload = """{"action": "member_added"}"""
        val event = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "organization",
            action = "member_added",
            payload = payload
        )

        handler.handle(event)

        verifyNoInteractions(usuarioRepository)
    }
}
