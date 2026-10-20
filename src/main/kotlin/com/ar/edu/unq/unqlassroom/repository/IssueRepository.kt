package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.Issue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface IssueRepository : JpaRepository<Issue, Long> {
    fun findByRepositorioIdAndEliminadoFalseOrderByNumeroDesc(repositorioId: Long): List<Issue>
    fun findByRepositorioIdAndNumero(repositorioId: Long, numero: Int): Issue?
}
