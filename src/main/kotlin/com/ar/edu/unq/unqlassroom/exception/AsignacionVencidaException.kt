package com.ar.edu.unq.unqlassroom.exception

class AsignacionVencidaException(
    message: String = "No se puede entregar la asignación porque la fecha límite ha vencido",
) : BadRequestException(message)
