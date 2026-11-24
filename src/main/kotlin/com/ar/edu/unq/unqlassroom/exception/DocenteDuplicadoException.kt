package com.ar.edu.unq.unqlassroom.exception

class DocenteDuplicadoException(
    docentes: List<String>,
    message: String = "Los siguientes docentes ya están asignados al curso: ${docentes.joinToString()}",
) : DuplicateResourceException(message, errorCode = "DOCENTE_DUPLICADO")
