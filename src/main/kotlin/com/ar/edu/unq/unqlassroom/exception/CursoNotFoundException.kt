package com.ar.edu.unq.unqlassroom.exception

class CursoNotFoundException(
    message: String = "Curso no encontrado",
    errorCode: String = "CURSO_NOT_FOUND",
) : ResourceNotFoundException(message, errorCode)
