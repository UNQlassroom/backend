package com.ar.edu.unq.unqlassroom.exception

class AlumnoEsDocenteDelCursoException(
    docentes: List<String>,
    message: String = "Los siguientes usuarios ya forman parte del equipo docente del curso: ${docentes.joinToString()}",
) : BadRequestException(message, errorCode = "ALUMNO_ES_DOCENTE_DEL_CURSO")
