package com.ar.edu.unq.unqlassroom.exception

class DniDuplicadoException(
    message: String = "El DNI ya se encuentra registrado en otra cuenta.",
) : DuplicateResourceException(
    message = message,
    errorCode = "DNI_DUPLICADO",
)
