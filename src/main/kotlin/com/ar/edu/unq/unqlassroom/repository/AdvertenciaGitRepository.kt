package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.AdvertenciaGit
import com.ar.edu.unq.unqlassroom.model.TipoAdvertenciaGit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface AdvertenciaGitRepository : JpaRepository<AdvertenciaGit, Long> {
    fun findByRepositorioIdOrderByFechaCreacionDesc(repositorioId: Long): List<AdvertenciaGit>
    fun findByGrupoAsignacionIdOrderByFechaCreacionDesc(grupoAsignacionId: Long): List<AdvertenciaGit>
    fun findByAsignacionIdOrderByFechaCreacionDesc(asignacionId: Long): List<AdvertenciaGit>
    fun findByAlumnoIdOrderByFechaCreacionDesc(alumnoId: Long): List<AdvertenciaGit>
    fun existsByRepositorioIdAndCommitShaAndTipo(
        repositorioId: Long,
        commitSha: String,
        tipo: TipoAdvertenciaGit
    ): Boolean

    @Modifying
    @Query("DELETE FROM AdvertenciaGit a WHERE a.repositorio.id IN :repoIds")
    fun deleteAllByRepositorioIdIn(@Param("repoIds") repoIds: Collection<Long>)
}

