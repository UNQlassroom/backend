package com.ar.edu.unq.unqlassroom.service

interface AsyncService {
    fun crearRepositoriosAsignacionAsincronicamente(asignacionId: Long, docenteUsername: String)
    fun eliminarRepositoriosAsincronicamente(repoNames: List<String>)
    fun archivarRepositoriosAsincronicamente(repoNames: List<String>) {}
    fun importarYEstandarizarTemplateAsincronicamente(
        targetRepoName: String,
        sourceRepoUrl: String,
    )
}
