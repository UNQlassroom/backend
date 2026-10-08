package com.ar.edu.unq.unqlassroom.exception

class UsuarioNotFoundException(
    message: String = "Usuario no encontrado",
    errorCode: String = "USUARIO_NOT_FOUND",
) : ResourceNotFoundException(message, errorCode)
