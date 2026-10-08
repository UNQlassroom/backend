package com.ar.edu.unq.unqlassroom.exception

class SinPermisoEntregaException(
    message: String = "No tiene permisos para entregar esta asignación",
    errorCode: String = "SIN_PERMISO_ENTREGA",
) : ForbiddenException(message, errorCode)
