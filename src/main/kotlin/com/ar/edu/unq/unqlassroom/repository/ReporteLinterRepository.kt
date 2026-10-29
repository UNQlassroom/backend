package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.ReporteLinter
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface ReporteLinterRepository : JpaRepository<ReporteLinter, Long> {
    fun findByRepositorioIdAndCommitSha(repositorioId: Long, commitSha: String): ReporteLinter?
    fun findFirstByRepositorioIdOrderByFechaActualizacionDesc(repositorioId: Long): ReporteLinter?
    fun findByRepositorioIdOrderByFechaActualizacionDesc(repositorioId: Long): List<ReporteLinter>
    fun findByGrupoAsignacionIdOrderByFechaActualizacionDesc(grupoAsignacionId: Long): List<ReporteLinter>
    fun findByAsignacionIdOrderByFechaActualizacionDesc(asignacionId: Long): List<ReporteLinter>
    fun findByAlumnoIdOrderByFechaActualizacionDesc(alumnoId: Long): List<ReporteLinter>

    @Modifying
    @Query("DELETE FROM IncidenciaLinter i WHERE i.reporte.id IN (SELECT r.id FROM ReporteLinter r WHERE r.repositorio.id IN :repoIds)")
    fun deleteIncidenciasByRepositorioIdIn(@Param("repoIds") repoIds: Collection<Long>)

    @Modifying
    @Query("DELETE FROM ReporteLinter r WHERE r.repositorio.id IN :repoIds")
    fun deleteReportesByRepositorioIdIn(@Param("repoIds") repoIds: Collection<Long>)

    @Modifying
    @Transactional
    fun deleteAllByRepositorioIdIn(repoIds: Collection<Long>) {
        if (repoIds.isNotEmpty()) {
            deleteIncidenciasByRepositorioIdIn(repoIds)
            deleteReportesByRepositorioIdIn(repoIds)
        }
    }
}
