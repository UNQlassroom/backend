package com.ar.edu.unq.unqlassroom.exception

class CursoDuplicadoException(
    message: String = "Ya existe un curso con los mismos datos",
    errorCode: String = "CURSO_DUPLICADO",
) : BadRequestException(message, errorCode) {
    constructor(materia: String, anio: Int, semestre: Int, comision: Int) : this(
        "Ya existe un curso para la materia '$materia' en el año $anio, semestre $semestre y comisión $comision"
    )
}
