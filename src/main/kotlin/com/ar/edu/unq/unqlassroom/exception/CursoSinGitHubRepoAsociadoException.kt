package com.ar.edu.unq.unqlassroom.exception

class CursoSinGitHubRepoAsociadoException(
    message: String = "Curso sin repositorio de GitHub asociado",
    errorCode: String = "CURSO_SIN_GITHUB_REPO_ASOCIADO",
) : BadRequestException(message, errorCode)
