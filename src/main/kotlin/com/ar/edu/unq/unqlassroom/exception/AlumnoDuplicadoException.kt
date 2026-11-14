package com.ar.edu.unq.unqlassroom.exception

class AlumnoDuplicadoException(
    alumnos: List<String>,
    message: String = "Los siguientes alumnos ya están inscriptos en el curso: ${alumnos.joinToString()}",
) : DuplicateResourceException(message, errorCode = "ALUMNO_DUPLICADO")
