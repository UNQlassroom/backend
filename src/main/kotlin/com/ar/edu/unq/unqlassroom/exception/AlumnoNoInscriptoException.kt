package com.ar.edu.unq.unqlassroom.exception

class AlumnoNoInscriptoException(
    username: String,
    message: String = "El alumno '$username' no está inscripto en el curso",
) : ResourceNotFoundException(message, errorCode = "ALUMNO_NO_INSCRIPTO")
