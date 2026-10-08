package com.ar.edu.unq.unqlassroom.exception

class GrupoNoPerteneceAAsignacionException(
    message: String = "El grupo especificado no pertenece a la asignación",
    errorCode: String = "GRUPO_NO_PERTENECE_A_ASIGNACION",
) : BadRequestException(message, errorCode)
