package com.ar.edu.unq.unqlassroom.exception

class DocenteNoAsignadoException(
    username: String,
    message: String = "El docente '$username' no está asignado a este curso",
) : ResourceNotFoundException(message, errorCode = "DOCENTE_NO_ASIGNADO")
