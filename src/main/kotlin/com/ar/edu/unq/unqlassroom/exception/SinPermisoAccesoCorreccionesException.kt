package com.ar.edu.unq.unqlassroom.exception

class SinPermisoAccesoCorreccionesException(
    message: String = "No tiene permisos para ver las correcciones de esta asignación",
) : ForbiddenException(message)
