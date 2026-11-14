package com.ar.edu.unq.unqlassroom.exception

class SinPermisoEntregaException(
    message: String = "No tiene permisos para entregar esta asignación",
) : ForbiddenException(message, errorCode = "SIN_PERMISO_ENTREGA")
