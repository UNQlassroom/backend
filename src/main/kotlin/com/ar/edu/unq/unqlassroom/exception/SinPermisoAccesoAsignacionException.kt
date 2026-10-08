package com.ar.edu.unq.unqlassroom.exception

class SinPermisoAccesoAsignacionException(
    message: String = "No tiene permisos para ver esta asignación",
    errorCode: String = "SIN_PERMISO_ACCESO_ASIGNACION",
) : ForbiddenException(message, errorCode)
