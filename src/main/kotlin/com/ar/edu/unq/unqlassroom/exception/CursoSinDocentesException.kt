package com.ar.edu.unq.unqlassroom.exception

class CursoSinDocentesException(
    message: String = "No se puede dar de baja al docente porque el curso debe tener al menos un docente",
) : BadRequestException(message, errorCode = "CURSO_SIN_DOCENTES")
