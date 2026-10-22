package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.exception.GitHubNotFoundException
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.repository.AsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.IssueRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class AsignacionAsyncService(
    private val gitHubRepoService: GitHubRepoService,
    private val asignacionRepository: AsignacionRepository,
    private val issueRepository: IssueRepository,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Async("taskExecutor")
    fun ejecutarEliminacionAsincronica(asignacionId: Long, repoNames: List<String>) {
        logger.info(
            "Iniciando proceso asincrónico de eliminación para asignación ID: {}. Repositorios a eliminar: {}",
            asignacionId,
            repoNames
        )

        for (repoName in repoNames) {
            try {
                gitHubRepoService.deleteRepository(repoName)
                logger.info("Repositorio '{}' eliminado exitosamente de GitHub", repoName)
            } catch (e: GitHubNotFoundException) {
                logger.warn("El repositorio '{}' no fue encontrado en GitHub al eliminar la asignación {}", repoName, asignacionId)
            } catch (e: Exception) {
                logger.error("Error al eliminar el repositorio '{}' de GitHub para asignación {}: {}", repoName, asignacionId, e.message, e)
            }
        }

        transactionTemplate.execute {
            val asignacionOpt = asignacionRepository.findById(asignacionId)
            if (asignacionOpt.isEmpty) {
                logger.warn("La asignación ID: {} ya no se encuentra en la base de datos", asignacionId)
                return@execute
            }

            val asignacion = asignacionOpt.get()
            val repoIds = asignacion.grupos.mapNotNull { it.repositorio?.id }
            if (repoIds.isNotEmpty()) {
                issueRepository.deleteAllByRepositorioIdIn(repoIds)
            }

            asignacion.curso?.asignaciones?.remove(asignacion)
            asignacionRepository.delete(asignacion)
            logger.info("Asignación ID: {} y registros asociados eliminados de la base de datos", asignacionId)
        }
    }
}
