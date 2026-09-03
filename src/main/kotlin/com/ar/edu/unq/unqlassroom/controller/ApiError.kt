package com.ar.edu.unq.unqlassroom.controller

import java.time.LocalDateTime

data class ApiError(
    val status: Int,
    val error: String,
    val message: String?,
    val path: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
)
