package com.ar.edu.unq.unqlassroom.exception

open class ConflictException(
    message: String,
    open val errorCode: String? = null,
) : RuntimeException(message)
