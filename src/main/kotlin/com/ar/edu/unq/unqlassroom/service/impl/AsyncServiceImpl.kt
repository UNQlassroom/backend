package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.exception.GitHubNotFoundException
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.service.AsyncService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class AsyncServiceImpl(
    private val gitHubRepoService: GitHubRepoService,
) : AsyncService {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Async
    override fun eliminarRepositoriosAsincronicamente(repoNames: List<String>) {
        if (repoNames.isEmpty()) {
            return
        }

        logger.info(
            "Iniciando eliminación asincrónica de repositorios en GitHub: {}",
            repoNames
        )

        for (repoName in repoNames) {
            try {
                gitHubRepoService.deleteRepository(repoName)
                logger.info("Repositorio '{}' eliminado exitosamente de GitHub", repoName)
            } catch (e: GitHubNotFoundException) {
                logger.warn("El repositorio '{}' no fue encontrado en GitHub al intentar eliminarlo", repoName)
            } catch (e: Exception) {
                logger.error("Error al eliminar el repositorio '{}' de GitHub: {}", repoName, e.message, e)
            }
        }
    }
}
