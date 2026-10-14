package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Handler de eventos de Webhook para membresía y organización de GitHub.
 *
 * Procesa cuando un usuario acepta la invitación a la organización de GitHub:
 * Evento `organization` con `action = "member_added"`.
 *
 * Al detectarse la aceptación, busca el `Usuario` correspondiente y actualiza su
 * `githubState` a "active".
 */
@Component
class MembershipWebhookHandler(
    private val usuarioRepository: UsuarioRepository,
    private val objectMapper: ObjectMapper,
) : WebhookEventHandler {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun canHandle(event: WebhookEvent): Boolean {
        val eventType = event.eventType.lowercase()
        return eventType == "organization"
    }

    @Transactional
    override fun handle(event: WebhookEvent) {
        val rootNode = objectMapper.readTree(event.payload)
        val action = event.action ?: rootNode.path("action").asText("")

        val esAceptacion = when (event.eventType.lowercase()) {
            "organization" -> action.equals("member_added", ignoreCase = true)
            else -> false
        }

        if (!esAceptacion) {
            logger.info(
                "Evento '{}' con acción '{}' ignorado por MembershipWebhookHandler (no es aceptación de membresía)",
                event.eventType,
                action
            )
            return
        }

        val username = extraerUsername(rootNode)
        if (username.isNullOrBlank()) {
            logger.warn("No se pudo extraer el username del miembro en el evento {} de tipo '{}'", event.id, event.eventType)
            return
        }

        val usuario = usuarioRepository.findByUsername(username)
        if (usuario == null) {
            logger.info("El usuario '{}' no existe en la base de datos de UNQlassroom", username)
            return
        }

        if (usuario.activarSiCorresponde("active")) {
            usuarioRepository.save(usuario)
            logger.info("Nuevo usuario activo en la organización: '{}'", username)
        } else {
            logger.info("El usuario '{}' ya se encontraba en estado activo en la organización", username)
        }
    }

    private fun extraerUsername(rootNode: com.fasterxml.jackson.databind.JsonNode): String? {
        val membershipUser = rootNode.path("membership").path("user").path("login").asText(null)
        if (!membershipUser.isNullOrBlank()) return membershipUser
        return null
    }
}
