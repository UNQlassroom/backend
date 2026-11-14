package com.ar.edu.unq.unqlassroom.exception

class NombreUsuarioInvalidoException(
    motivo: String = "El nombre y apellido contiene caracteres inválidos o excede el límite de 35 caracteres",
) : BadRequestException(
    message = motivo,
    errorCode = "NOMBRE_USUARIO_INVALIDO",
)
