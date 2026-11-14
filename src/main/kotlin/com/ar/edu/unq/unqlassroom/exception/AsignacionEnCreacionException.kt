package com.ar.edu.unq.unqlassroom.exception

class AsignacionEnCreacionException(
    message: String = "No se puede eliminar la asignación mientras sus repositorios se están creando",
) : BadRequestException(message, errorCode = "ASIGNACION_EN_CREACION")
