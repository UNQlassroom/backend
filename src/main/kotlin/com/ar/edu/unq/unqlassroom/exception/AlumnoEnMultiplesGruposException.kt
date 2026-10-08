package com.ar.edu.unq.unqlassroom.exception

class AlumnoEnMultiplesGruposException(
    message: String = "Un alumno no puede pertenecer a más de un grupo en la misma asignación",
    errorCode: String = "ALUMNO_EN_MULTIPLES_GRUPOS",
) : BadRequestException(message, errorCode)
