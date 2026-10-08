package com.ar.edu.unq.unqlassroom.exception

class AsignacionGrupalSinGruposException(
    message: String = "Para una asignación grupal debe especificar al menos un grupo",
    errorCode: String = "ASIGNACION_GRUPAL_SIN_GRUPOS",
) : BadRequestException(message, errorCode)
