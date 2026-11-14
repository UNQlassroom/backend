package com.ar.edu.unq.unqlassroom.exception

class CursoArchivadoException(
    message: String = "El curso se encuentra archivado y es de solo lectura",
) : ConflictException(message, "CURSO_ARCHIVADO")
