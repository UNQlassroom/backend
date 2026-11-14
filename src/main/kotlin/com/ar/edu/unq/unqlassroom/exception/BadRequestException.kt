package com.ar.edu.unq.unqlassroom.exception

open class BadRequestException(
    message: String,
    open val errorCode: String? = null,
) : RuntimeException(message)
