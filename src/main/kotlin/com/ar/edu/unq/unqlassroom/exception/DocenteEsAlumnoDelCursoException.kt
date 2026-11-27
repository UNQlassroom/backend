package com.ar.edu.unq.unqlassroom.exception

class DocenteEsAlumnoDelCursoException(
    alumnos: List<String>,
    message: String = "Los siguientes usuarios ya están inscriptos como alumnos en este curso: ${alumnos.joinToString()}",
) : BadRequestException(message, errorCode = "DOCENTE_ES_ALUMNO_DEL_CURSO")
