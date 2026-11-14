package com.ar.edu.unq.unqlassroom.exception

class LimiteArchivosTemplateExcedidoException(
    owner: String,
    repo: String,
    totalArchivos: Int,
    maxPermitido: Int = 300,
) : BadRequestException(
    message = "El repositorio '$owner/$repo' contiene demasiados archivos ($totalArchivos). El límite máximo permitido para importar es de $maxPermitido archivos.",
    errorCode = "LIMITE_ARCHIVOS_TEMPLATE_EXCEDIDO",
)
