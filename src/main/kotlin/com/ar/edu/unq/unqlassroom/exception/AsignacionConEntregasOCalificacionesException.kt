package com.ar.edu.unq.unqlassroom.exception

class AsignacionConEntregasOCalificacionesException(
    message: String = "No se puede eliminar la asignación porque ya cuenta con entregas o calificaciones registradas",
) : BadRequestException(message, errorCode = "ASIGNACION_CON_ENTREGAS_O_CALIFICACIONES")
