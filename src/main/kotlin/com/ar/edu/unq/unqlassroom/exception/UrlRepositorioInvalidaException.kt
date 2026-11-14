package com.ar.edu.unq.unqlassroom.exception

class UrlRepositorioInvalidaException(url: String) : BadRequestException(
    message = "El formato del repositorio a importar es inválido: '$url'. Debe ser una URL completa de GitHub (ej. 'https://github.com/usuario/repositorio').",
    errorCode = "URL_REPOSITORIO_INVALIDA",
)
