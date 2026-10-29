package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.MetricaCI
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface MetricaCIRepository : JpaRepository<MetricaCI, Long> {
    fun findByRepositorioIdAndCommitSha(repositorioId: Long, commitSha: String): MetricaCI?
    fun findFirstByRepositorioIdOrderByFechaActualizacionDesc(repositorioId: Long): MetricaCI?
    fun findByRepositorioIdOrderByFechaActualizacionDesc(repositorioId: Long): List<MetricaCI>
    fun findByGrupoAsignacionIdOrderByFechaActualizacionDesc(grupoAsignacionId: Long): List<MetricaCI>
    fun findByAsignacionIdOrderByFechaActualizacionDesc(asignacionId: Long): List<MetricaCI>
    fun findByAlumnoIdOrderByFechaActualizacionDesc(alumnoId: Long): List<MetricaCI>

    @Modifying
    @Query("DELETE FROM MetricaCI m WHERE m.repositorio.id IN :repoIds")
    fun deleteAllByRepositorioIdIn(@Param("repoIds") repoIds: Collection<Long>)
}

