package com.ar.edu.unq.unqlassroom.errors

import com.ar.edu.unq.unqlassroom.errors.ConflictException

class CursoSinGitHubTeamAsociadoException(message: String = "Curso sin GitHub Team asociado") : ConflictException(message)