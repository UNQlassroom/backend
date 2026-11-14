package com.ar.edu.unq.unqlassroom.exception

class GrupoIdRequeridoParaDocenteException(
    message: String = "Debe especificar el grupoId para marcar la entrega como docente",
) : BadRequestException(message, errorCode = "GRUPO_ID_REQUERIDO_PARA_DOCENTE")
