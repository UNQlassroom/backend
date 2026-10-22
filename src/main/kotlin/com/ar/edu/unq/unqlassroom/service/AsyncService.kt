package com.ar.edu.unq.unqlassroom.service

interface AsyncService {
    fun eliminarRepositoriosAsincronicamente(repoNames: List<String>)
}
