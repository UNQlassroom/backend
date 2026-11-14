package com.ar.edu.unq.unqlassroom.exception

open class DuplicateResourceException(
    message: String,
    open val errorCode: String? = null,
) : RuntimeException(message)
