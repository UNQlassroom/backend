package com.ar.edu.unq.unqlassroom.exception

class SinPermisoAccesoCorreccionesException(
    message: String = "No tiene permisos para ver las correcciones de esta asignación",
    errorCode: String = "SIN_PERMISO_ACCESO_CORRECCIONES",
) : ForbiddenException(message, errorCode)
