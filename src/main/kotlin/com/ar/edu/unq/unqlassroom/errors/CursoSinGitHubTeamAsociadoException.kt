package com.ar.edu.unq.unqlassroom.errors

class CursoSinGitHubTeamAsociadoException(
    message: String = "Curso sin GitHub Team asociado",
) : BadRequestException(message)