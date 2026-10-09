package com.ar.edu.unq.unqlassroom.exception

class AnioCursoInvalidoException(
    message: String = "El año es inválido",
) : BadRequestException(message, errorCode = "ANIO_CURSO_INVALIDO")
