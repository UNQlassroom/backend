package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.GrupoAsignacion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface GrupoAsignacionRepository : JpaRepository<GrupoAsignacion, Long> {
    @Query("SELECT g FROM GrupoAsignacion g JOIN g.repositorio r WHERE LOWER(r.nombre) = LOWER(:nombre)")
    fun findByRepositorioNombreIgnoreCase(@Param("nombre") nombre: String): GrupoAsignacion?
}
