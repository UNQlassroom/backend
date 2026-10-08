package com.ar.edu.unq.unqlassroom.exception

class UsuarioNoPerteneceAGrupoException(
    message: String = "El usuario no pertenece a ningún grupo de esta asignación",
    errorCode: String = "USUARIO_NO_PERTENECE_A_GRUPO",
) : BadRequestException(message, errorCode)
