package com.ar.edu.unq.unqlassroom.exception

class GrupoIdRequeridoParaDocenteException(
    message: String = "Debe especificar el grupoId para marcar la entrega como docente",
) : BadRequestException(message)
