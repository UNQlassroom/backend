package com.ar.edu.unq.unqlassroom.exception

class AsignacionNotFoundException(
    message: String = "Asignación no encontrada",
    errorCode: String = "ASIGNACION_NOT_FOUND",
) : ResourceNotFoundException(message, errorCode)
