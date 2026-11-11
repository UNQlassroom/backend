package com.ar.edu.unq.unqlassroom.exception

class RepositorioExternoPrivadoException(owner: String, repo: String) : BadRequestException(
    message = "El repositorio '$owner/$repo' es privado. Solo se permite importar repositorios públicos.",
    errorCode = "REPOSITORIO_EXTERNO_ES_PRIVADO",
)
