package com.ar.edu.unq.unqlassroom.exception

class AsignacionIndividualConGruposException(
    message: String = "No se pueden especificar grupos para una asignación individual",
) : BadRequestException(message, errorCode = "ASIGNACION_INDIVIDUAL_CON_GRUPOS")
