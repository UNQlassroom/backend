package com.ar.edu.unq.unqlassroom.exception

class AsignacionGrupalSinGruposException(
    message: String = "Para una asignación grupal debe especificar al menos un grupo",
) : BadRequestException(message)
