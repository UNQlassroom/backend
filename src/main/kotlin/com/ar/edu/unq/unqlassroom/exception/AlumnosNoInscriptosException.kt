package com.ar.edu.unq.unqlassroom.exception

class AlumnosNoInscriptosException(
    alumnos: List<String>,
    message: String = "Los siguientes alumnos no están inscriptos en el curso: ${alumnos.joinToString()}",
) : BadRequestException(message, errorCode = "ALUMNOS_NO_INSCRIPTOS")
