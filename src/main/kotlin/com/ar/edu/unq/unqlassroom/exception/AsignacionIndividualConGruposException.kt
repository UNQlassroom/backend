package com.ar.edu.unq.unqlassroom.exception

class AsignacionIndividualConGruposException(
    message: String = "No se pueden especificar grupos para una asignación individual",
    errorCode: String = "ASIGNACION_INDIVIDUAL_CON_GRUPOS",
) : BadRequestException(message, errorCode)
