package com.ar.edu.unq.unqlassroom.exception

class AlumnosNoInscriptosException(
    alumnos: List<String>,
    message: String = "Los siguientes alumnos no están inscriptos en el curso: ${alumnos.joinToString()}",
    errorCode: String = "ALUMNOS_NO_INSCRIPTOS",
) : BadRequestException(message, errorCode)
