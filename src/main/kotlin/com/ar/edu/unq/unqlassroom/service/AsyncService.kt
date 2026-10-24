package com.ar.edu.unq.unqlassroom.service

interface AsyncService {
    fun crearRepositoriosAsignacionAsincronicamente(asignacionId: Long, docenteUsername: String)
    fun eliminarRepositoriosAsincronicamente(repoNames: List<String>)
}
