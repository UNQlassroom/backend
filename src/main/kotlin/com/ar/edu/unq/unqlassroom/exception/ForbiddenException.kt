package com.ar.edu.unq.unqlassroom.exception

open class ForbiddenException(
    message: String,
    open val errorCode: String? = null,
) : RuntimeException(message)
