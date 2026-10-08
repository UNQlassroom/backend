package com.ar.edu.unq.unqlassroom.exception

class NombreGrupoDuplicadoException(
    message: String = "No puede haber grupos con el mismo nombre en la misma asignación",
    errorCode: String = "NOMBRE_GRUPO_DUPLICADO",
) : BadRequestException(message, errorCode)
