package com.ar.edu.unq.unqlassroom.exception

class GrupoSinIntegrantesException(
    message: String = "Todos los grupos deben tener al menos un integrante",
) : BadRequestException(message, errorCode = "GRUPO_SIN_INTEGRANTES")
