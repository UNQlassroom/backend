package com.ar.edu.unq.unqlassroom.exception
 
class CursoSinGitHubRepoAsociadoException(
    message: String = "Curso sin repositorio de GitHub asociado",
) : BadRequestException(message)
