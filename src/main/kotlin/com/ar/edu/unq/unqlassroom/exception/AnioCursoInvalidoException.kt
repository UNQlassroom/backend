package com.ar.edu.unq.unqlassroom.exception

class AnioCursoInvalidoException(
    message: String = "El año es inválido",
    errorCode: String = "ANIO_CURSO_INVALIDO",
) : BadRequestException(message, errorCode)
