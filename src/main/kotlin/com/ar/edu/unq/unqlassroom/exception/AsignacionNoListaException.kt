package com.ar.edu.unq.unqlassroom.exception

class AsignacionNoListaException(
    message: String = "La asignación aún no se encuentra lista para operar",
) : BadRequestException(message, errorCode = "ASIGNACION_NO_LISTA")
