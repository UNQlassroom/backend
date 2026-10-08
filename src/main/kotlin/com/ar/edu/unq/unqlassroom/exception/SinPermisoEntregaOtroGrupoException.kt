package com.ar.edu.unq.unqlassroom.exception

class SinPermisoEntregaOtroGrupoException(
    message: String = "No tiene permisos para entregar en nombre de otro grupo",
) : ForbiddenException(message)
