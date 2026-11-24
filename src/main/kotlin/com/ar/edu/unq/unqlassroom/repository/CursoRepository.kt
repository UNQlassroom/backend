package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.Curso
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface CursoRepository : JpaRepository<Curso, Long> {

    @Query("SELECT DISTINCT c FROM Curso c JOIN c.docentes d WHERE d.username = :username AND c.archivado = :archivado")
    fun findCursosParaDocente(
        @Param("username") username: String,
        @Param("archivado") archivado: Boolean = false,
    ): List<Curso>

    @Query("SELECT DISTINCT c FROM Curso c JOIN c.inscripciones i WHERE i.usuario.username = :username AND i.activa = true AND c.archivado = false AND NOT EXISTS (SELECT 1 FROM c.docentes d WHERE d.username = :username)")
    fun findCursosParaAlumno(@Param("username") username: String): List<Curso>

    fun findByArchivadoFalse(): List<Curso>

    @Query("SELECT DISTINCT d.username FROM Curso c JOIN c.docentes d")
    fun findDocentesUsernames(): List<String>

    fun existsByMateriaIgnoreCaseAndAnioAndSemestreAndComision(
        materia: String,
        anio: Int,
        semestre: Int,
        comision: Int
    ): Boolean
}
