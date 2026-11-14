package com.ar.edu.unq.unqlassroom.exception

class SinPermisoAccesoAsignacionException(
    message: String = "No tiene permisos para ver esta asignación",
) : ForbiddenException(message, errorCode = "SIN_PERMISO_ACCESO_ASIGNACION")
