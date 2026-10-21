package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.Issue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface IssueRepository : JpaRepository<Issue, Long> {
    fun findByRepositorioIdAndEliminadoFalseOrderByNumeroDesc(repositorioId: Long): List<Issue>
    fun findByRepositorioIdAndNumero(repositorioId: Long, numero: Int): Issue?

    @Modifying
    @Query("DELETE FROM Issue i WHERE i.repositorio.id IN :repoIds")
    fun deleteAllByRepositorioIdIn(@Param("repoIds") repoIds: Collection<Long>)
}
