package com.ar.edu.unq.unqlassroom.controller.advice

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiError(
    val status: Int,
    val error: String,
    val message: String?,
    val errorCode: String? = null,
    val path: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
)
