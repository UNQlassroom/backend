package com.ar.edu.unq.unqlassroom.exception

class SinPermisoEntregaOtroGrupoException(
    message: String = "No tiene permisos para entregar en nombre de otro grupo",
    errorCode: String = "SIN_PERMISO_ENTREGA_OTRO_GRUPO",
) : ForbiddenException(message, errorCode)
