package com.ar.edu.unq.unqlassroom.exception

class CalificacionInvalidaException(
    message: String = "La nota debe ser entre 1 y 10",
    errorCode: String = "CALIFICACION_INVALIDA",
) : BadRequestException(message, errorCode)
