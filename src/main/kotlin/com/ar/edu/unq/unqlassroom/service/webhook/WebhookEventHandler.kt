package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent

/**
 * Interfaz común para los handlers de eventos de Webhook de GitHub.
 *
 * Cada implementación (por ejemplo, MembershipWebhookHandler, PushWebhookHandler)
 * se registra automáticamente como componente de Spring y define qué eventos procesar.
 */
interface WebhookEventHandler {

    /**
     * Determina si este handler puede procesar el evento recibido.
     *
     * Permite discriminar por [WebhookEvent.eventType] (ej: "membership", "push")
     * y opcionalmente por [WebhookEvent.action] (ej: "added", "removed").
     *
     * @param event El evento de webhook a evaluar.
     * @return `true` si el handler puede procesar el evento, `false` en caso contrario.
     */
    fun canHandle(event: WebhookEvent): Boolean

    /**
     * Ejecuta la lógica de negocio asociada al evento recibido.
     *
     * @param event El evento de webhook a procesar con su payload y metadatos.
     * @throws Exception Si ocurre un fallo en el procesamiento de dominio.
     */
    fun handle(event: WebhookEvent)
}
