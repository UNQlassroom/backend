package com.ar.edu.unq.unqlassroom.exception

class AsignacionDuplicadaException(
    titulo: String? = null,
    message: String = if (titulo != null) "Ya existe una asignación con el título '$titulo' en este curso" else "Ya existe una asignación con ese título en este curso",
    errorCode: String = "ASIGNACION_DUPLICADA",
) : BadRequestException(message, errorCode)
