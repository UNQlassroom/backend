package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.Inscripcion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface InscripcionRepository : JpaRepository<Inscripcion, Long> {
    fun findByCursoId(cursoId: Long): List<Inscripcion>
    fun findByCursoIdAndUsuarioUsername(cursoId: Long, username: String): Inscripcion?
}
