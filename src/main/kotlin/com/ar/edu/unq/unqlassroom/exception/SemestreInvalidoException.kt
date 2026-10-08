package com.ar.edu.unq.unqlassroom.exception

class SemestreInvalidoException(
    message: String = "El semestre debe ser 1 o 2",
) : BadRequestException(message)
