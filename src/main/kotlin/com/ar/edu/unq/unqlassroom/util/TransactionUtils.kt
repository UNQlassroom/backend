package com.ar.edu.unq.unqlassroom.util

import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

private val logger = LoggerFactory.getLogger("com.ar.edu.unq.unqlassroom.util.TransactionUtils")

fun ejecutarDespuesDeCommit(accion: () -> Unit) {
    val accionSegura = {
        try {
            accion()
        } catch (e: Exception) {
            logger.warn("Error ejecutando acción post-commit: {}", e.message, e)
        }
    }

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                accionSegura()
            }
        })
    } else {
        accionSegura()
    }
}
