package com.ar.edu.unq.unqlassroom.exception

open class ResourceNotFoundException(
    message: String? = null,
    open val errorCode: String? = null,
) : RuntimeException(message)
