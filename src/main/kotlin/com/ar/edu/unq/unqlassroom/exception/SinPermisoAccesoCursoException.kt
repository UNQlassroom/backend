package com.ar.edu.unq.unqlassroom.exception

class SinPermisoAccesoCursoException(
    message: String = "No tiene permisos para acceder a este curso",
    errorCode: String = "SIN_PERMISO_ACCESO_CURSO",
) : ForbiddenException(message, errorCode)
