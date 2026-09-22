package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.Curso
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CursoRepository : JpaRepository<Curso, Long> {

    @Query("SELECT c FROM Curso c WHERE c.owner.username = :username")
    fun findCursosParaDocente(@Param("username") username: String): List<Curso>

    @Query("SELECT c FROM Curso c JOIN c.inscripciones i WHERE i.usuario.username = :username AND (c.owner IS NULL OR c.owner.username <> :username)")
    fun findCursosParaAlumno(@Param("username") username: String): List<Curso>
}