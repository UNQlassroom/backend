package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEventStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface WebhookEventRepository : JpaRepository<WebhookEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WebhookEvent w WHERE w.status = :status ORDER BY w.createdAt ASC")
    fun findByStatusForUpdate(
        @Param("status") status: WebhookEventStatus,
        pageable: Pageable
    ): List<WebhookEvent>

    fun findByStatusOrderByCreatedAtAsc(
        status: WebhookEventStatus,
        pageable: Pageable
    ): List<WebhookEvent>

    fun existsByDeliveryId(deliveryId: String): Boolean
}
