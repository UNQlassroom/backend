package com.ar.edu.unq.unqlassroom.exception

class UsuariosGitHubNoEncontradosException(
    usuarios: List<String>,
    message: String = "Los siguientes usuarios no existen en GitHub: ${usuarios.joinToString()}",
) : BadRequestException(message, errorCode = "GITHUB_USERS_NOT_FOUND")
