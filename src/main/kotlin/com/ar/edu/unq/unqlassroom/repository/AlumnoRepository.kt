package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.Alumno
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AlumnoRepository : JpaRepository<Alumno, Long> {
    fun findByCursoId(cursoId: Long): List<Alumno>
    fun findByCursoIdAndUsername(cursoId: Long, username: String): Alumno?
}
