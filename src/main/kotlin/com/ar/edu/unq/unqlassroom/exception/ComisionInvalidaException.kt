package com.ar.edu.unq.unqlassroom.exception

class ComisionInvalidaException(
    message: String = "La comisión debe ser mayor a 0",
) : BadRequestException(message, errorCode = "COMISION_INVALIDA")
