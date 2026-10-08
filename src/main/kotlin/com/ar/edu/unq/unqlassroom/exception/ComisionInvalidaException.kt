package com.ar.edu.unq.unqlassroom.exception

class ComisionInvalidaException(
    message: String = "La comisión debe ser mayor a 0",
    errorCode: String = "COMISION_INVALIDA",
) : BadRequestException(message, errorCode)
