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
 * Procesa exclusivamente cuando un usuario acepta la invitación a la organización de GitHub:
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
        if (!event.eventType.equals("organization", ignoreCase = true)) {
            return false
        }
        val action = event.action ?: extraerActionDePayload(event.payload)
        return action.equals("member_added", ignoreCase = true)
    }

    @Transactional
    override fun handle(event: WebhookEvent) {
        val rootNode = objectMapper.readTree(event.payload)
        val username = extraerUsername(rootNode)
        if (username.isNullOrBlank()) {
            logger.warn("No se pudo extraer el username del miembro en el evento {} de tipo '{}'", event.id, event.eventType)
            return
        }

        val usuario = usuarioRepository.findByUsernameIgnoreCase(username)
            ?: usuarioRepository.findByUsername(username)
        if (usuario == null) {
            logger.info("El usuario '{}' no existe en la base de datos de UNQlassroom", username)
            return
        }

        val casingCambio = usuario.username != username
        if (casingCambio) {
            usuario.username = username
        }

        val estadoCambio = usuario.activarSiCorresponde("active")

        if (estadoCambio || casingCambio) {
            usuarioRepository.save(usuario)
            if (estadoCambio) {
                logger.info("Nuevo usuario activo en la organización: '{}'", username)
            } else {
                logger.info("Username actualizado a '{}' para usuario ya activo en la organización", username)
            }
        } else {
            logger.info("El usuario '{}' ya se encontraba en estado activo en la organización", username)
        }
    }

    private fun extraerActionDePayload(payload: String): String? {
        return try {
            val node = objectMapper.readTree(payload).get("action")
            if (node != null && !node.isNull) node.asText() else null
        } catch (_: Exception) {
            null
        }
    }

    private fun extraerUsername(rootNode: com.fasterxml.jackson.databind.JsonNode): String? {
        val membershipUser = rootNode.path("membership").path("user").path("login").asText(null)
        if (!membershipUser.isNullOrBlank()) return membershipUser
        val memberUser = rootNode.path("member").path("login").asText(null)
        if (!memberUser.isNullOrBlank()) return memberUser
        return null
    }
}
