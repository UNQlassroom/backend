package com.ar.edu.unq.unqlassroom.exception

class NoEsDocenteDelCursoException(
    message: String = "Solo el docente a cargo del curso puede realizar esta operación",
    errorCode: String = "NO_ES_DOCENTE_DEL_CURSO",
) : ForbiddenException(message, errorCode)
