package com.ar.edu.unq.unqlassroom.exception

class UsuarioNoPerteneceAGrupoException(
    message: String = "El usuario no pertenece a ningún grupo de esta asignación",
) : BadRequestException(message)
