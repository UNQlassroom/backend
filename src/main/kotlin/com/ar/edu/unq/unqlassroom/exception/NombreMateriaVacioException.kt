package com.ar.edu.unq.unqlassroom.exception

class NombreMateriaVacioException(
    message: String = "El nombre de la materia no puede estar vacío",
) : BadRequestException(message, errorCode = "NOMBRE_MATERIA_VACIO")
