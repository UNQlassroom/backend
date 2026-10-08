package com.ar.edu.unq.unqlassroom.exception

class UsuarioNoEsDocenteException(
    username: String? = null,
    message: String = if (username != null) "El usuario $username no tiene permisos de docente" else "El usuario no tiene permisos de docente",
    errorCode: String = "USUARIO_NO_ES_DOCENTE",
) : ForbiddenException(message, errorCode)
