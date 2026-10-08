package com.ar.edu.unq.unqlassroom.exception

class NombreGrupoVacioException(
    message: String = "El nombre del grupo no puede estar vacío",
) : BadRequestException(message)
