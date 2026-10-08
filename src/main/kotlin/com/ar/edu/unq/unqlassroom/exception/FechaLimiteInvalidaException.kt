package com.ar.edu.unq.unqlassroom.exception

class FechaLimiteInvalidaException(
    message: String = "La fecha límite no puede ser anterior a la fecha actual",
) : BadRequestException(message)
