package com.ar.edu.unq.unqlassroom.exception

open class UnauthorizedException(
    message: String,
    open val errorCode: String? = null,
) : RuntimeException(message)
