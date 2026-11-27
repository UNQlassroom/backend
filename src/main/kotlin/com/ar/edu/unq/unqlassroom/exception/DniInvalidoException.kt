package com.ar.edu.unq.unqlassroom.exception

class DniInvalidoException(
    motivo: String = "El DNI debe contener entre 7 y 8 dígitos numéricos",
) : BadRequestException(
    message = motivo,
    errorCode = "DNI_INVALIDO",
)
