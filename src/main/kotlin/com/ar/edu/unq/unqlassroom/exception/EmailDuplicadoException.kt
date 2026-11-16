package com.ar.edu.unq.unqlassroom.exception

class EmailDuplicadoException(
    message: String = "El correo electrónico ya se encuentra registrado en otra cuenta",
) : DuplicateResourceException(
    message = message,
    errorCode = "EMAIL_DUPLICADO",
)
