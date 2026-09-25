package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.Asignacion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AsignacionRepository : JpaRepository<Asignacion, Long> {
    fun findByCursoId(cursoId: Long): List<Asignacion>
    fun findByIdAndCursoId(id: Long, cursoId: Long): Asignacion?
}
