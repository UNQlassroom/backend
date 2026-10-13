package com.ar.edu.unq.unqlassroom.model.webhook

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "webhook_events",
    indexes = [
        Index(name = "idx_webhook_events_status_created_at", columnList = "status, created_at"),
        Index(name = "idx_webhook_events_delivery_id", columnList = "delivery_id", unique = true)
    ]
)
class WebhookEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Column(name = "delivery_id", nullable = true, unique = true)
    var deliveryId: String? = null,

    @Column(name = "event_type", nullable = false)
    var eventType: String,

    @Column(name = "action", nullable = true)
    var action: String? = null,

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    var payload: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: WebhookEventStatus = WebhookEventStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "processed_at", nullable = true)
    var processedAt: LocalDateTime? = null,

    @Column(name = "error_message", columnDefinition = "TEXT", nullable = true)
    var errorMessage: String? = null,
) {
    fun marcarProcesado() {
        this.status = WebhookEventStatus.PROCESSED
        this.processedAt = LocalDateTime.now()
        this.errorMessage = null
    }

    fun marcarError(mensaje: String?) {
        this.status = WebhookEventStatus.ERROR
        this.processedAt = LocalDateTime.now()
        this.errorMessage = mensaje
    }
}
