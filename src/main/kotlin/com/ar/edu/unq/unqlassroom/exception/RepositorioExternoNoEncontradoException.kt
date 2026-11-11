package com.ar.edu.unq.unqlassroom.exception

class RepositorioExternoNoEncontradoException(owner: String, repo: String) : BadRequestException(
    message = "El repositorio '$owner/$repo' no fue encontrado o es privado.",
    errorCode = "REPOSITORIO_EXTERNO_NOT_FOUND_OR_PRIVATE",
)
