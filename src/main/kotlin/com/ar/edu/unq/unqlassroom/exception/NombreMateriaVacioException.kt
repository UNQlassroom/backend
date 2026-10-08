package com.ar.edu.unq.unqlassroom.exception

class NombreMateriaVacioException(
    message: String = "El nombre de la materia no puede estar vacío",
    errorCode: String = "NOMBRE_MATERIA_VACIO",
) : BadRequestException(message, errorCode)
