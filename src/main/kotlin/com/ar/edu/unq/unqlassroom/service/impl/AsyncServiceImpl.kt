package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.exception.GitHubNotFoundException
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubCollaboratorService
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.TipoAsignacion
import com.ar.edu.unq.unqlassroom.repository.AsignacionRepository
import com.ar.edu.unq.unqlassroom.service.AsyncService
import com.ar.edu.unq.unqlassroom.service.TemplateStandardizationService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

@Service
class AsyncServiceImpl(
    private val gitHubRepoService: GitHubRepoService,
    private val gitHubCollaboratorService: GitHubCollaboratorService,
    private val asignacionRepository: AsignacionRepository,
    private val templateStandardizationService: TemplateStandardizationService? = null,
) : AsyncService {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Async
    @Transactional
    override fun crearRepositoriosAsignacionAsincronicamente(asignacionId: Long, docenteUsername: String) {
        logger.info("Iniciando creación asincrónica de repositorios para asignación ID: {}", asignacionId)

        val asignacion = asignacionRepository.findById(asignacionId).orElse(null)
        if (asignacion == null) {
            logger.warn("La asignación ID: {} no fue encontrada en la base de datos", asignacionId)
            return
        }

        val reposCreadosEnEjecucion = mutableListOf<String>()

        try {
            templateStandardizationService?.asegurarTemplateEstandarizado(asignacion.templateRepoName)

            for (grupo in asignacion.grupos) {
                val sufijo = if (asignacion.tipo == TipoAsignacion.INDIVIDUAL) {
                    grupo.integrantes.first().username
                } else {
                    grupo.nombre!!.trim()
                }

                val repoName = asignacion.generarNombreRepo(sufijo)
                val repoDesc = asignacion.generarDescripcionRepo(sufijo)

                if (!gitHubRepoService.repositoryExists(repoName)) {
                    gitHubRepoService.createRepositoryFromTemplate(
                        templateRepoName = asignacion.templateRepoName,
                        newRepoName = repoName,
                        description = repoDesc,
                        private = true,
                    )
                    reposCreadosEnEjecucion.add(repoName)
                }

                for (integrante in grupo.integrantes) {
                    gitHubCollaboratorService.addCollaborator(
                        repoName = repoName,
                        username = integrante.username,
                        permission = "push",
                    )
                }

                if (grupo.integrantes.none { it.username == docenteUsername }) {
                    gitHubCollaboratorService.addCollaborator(
                        repoName = repoName,
                        username = docenteUsername,
                        permission = "push",
                    )
                }

                val info = gitHubRepoService.obtenerInformacionRepositorio(repoName)
                grupo.repositorio = Repositorio(
                    nombre = info.nombre,
                    htmlUrl = info.htmlUrl,
                    ultimoCommit = info.ultimoCommit,
                    fechaUltimoCommit = info.fechaUltimoCommit,
                    estadoCI = info.estadoCI,
                )
            }

            asignacion.marcarComoLista()
            asignacionRepository.save(asignacion)

            logger.info("Creación asincrónica de repositorios completada con éxito para asignación ID: {}", asignacionId)
        } catch (e: Exception) {
            logger.error(
                "Error crítico creando repositorios en GitHub para asignación ID: {}. Iniciando rollback de repositorios creados: {}",
                asignacionId,
                reposCreadosEnEjecucion,
                e
            )

            asignacion.grupos.forEach { it.repositorio = null }
            asignacion.marcarComoError()
            asignacionRepository.save(asignacion)

            eliminarRepositoriosAsincronicamente(reposCreadosEnEjecucion)
        }
    }

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
