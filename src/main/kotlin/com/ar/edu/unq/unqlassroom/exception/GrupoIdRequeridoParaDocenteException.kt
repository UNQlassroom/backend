package com.ar.edu.unq.unqlassroom.exception

class GrupoIdRequeridoParaDocenteException(
    message: String = "Debe especificar el grupoId para marcar la entrega como docente",
    errorCode: String = "GRUPO_ID_REQUERIDO_PARA_DOCENTE",
) : BadRequestException(message, errorCode)
