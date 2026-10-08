package com.ar.edu.unq.unqlassroom.exception

class NoEsDocenteDelCursoException(
    message: String = "Solo el docente a cargo del curso puede realizar esta operación",
) : ForbiddenException(message)
