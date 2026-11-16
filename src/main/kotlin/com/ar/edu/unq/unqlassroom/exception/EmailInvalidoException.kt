package com.ar.edu.unq.unqlassroom.exception

class EmailInvalidoException(
    motivo: String = "El correo electrónico debe tener un formato válido, contener '@' y finalizar con '.com' o '.com.ar'",
) : BadRequestException(
    message = motivo,
    errorCode = "EMAIL_INVALIDO",
)
