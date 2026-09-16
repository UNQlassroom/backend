package com.ar.edu.unq.unqlassroom.errors
 
class CursoSinGitHubRepoAsociadoException(
    message: String = "Curso sin repositorio de GitHub asociado",
) : BadRequestException(message)
