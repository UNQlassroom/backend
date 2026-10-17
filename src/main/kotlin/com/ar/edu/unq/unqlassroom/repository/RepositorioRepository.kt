package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.Repositorio
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface RepositorioRepository : JpaRepository<Repositorio, Long> {
    fun findByNombreIgnoreCase(nombre: String): Repositorio?
}
