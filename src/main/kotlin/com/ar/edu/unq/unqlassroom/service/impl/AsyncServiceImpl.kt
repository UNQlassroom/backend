package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.exception.GitHubNotFoundException
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubCollaboratorService
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.TipoAsignacion
import com.ar.edu.unq.unqlassroom.repository.AsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.ar.edu.unq.unqlassroom.service.AsyncService
import com.ar.edu.unq.unqlassroom.service.SseNotificationService
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
    private val sseNotificationService: SseNotificationService? = null,
    private val repositorioRepository: RepositorioRepository? = null,
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
            val guardada = asignacionRepository.save(asignacion) ?: asignacion

            logger.info("Creación asincrónica de repositorios completada con éxito para asignación ID: {}", asignacionId)
            try {
                sseNotificationService?.notificarAsignacionActualizada(guardada)
            } catch (e: Exception) {
                logger.warn("Error enviando notificación SSE para asignacion lista ID {}: {}", asignacionId, e.message)
            }
        } catch (e: Exception) {
            logger.error(
                "Error crítico creando repositorios en GitHub para asignación ID: {}. Iniciando rollback de repositorios creados: {}",
                asignacionId,
                reposCreadosEnEjecucion,
                e
            )

            asignacion.grupos.forEach { it.repositorio = null }
            asignacion.marcarComoError()
            val fallida = asignacionRepository.save(asignacion) ?: asignacion

            try {
                sseNotificationService?.notificarAsignacionActualizada(fallida)
            } catch (notifEx: Exception) {
                logger.warn("Error enviando notificación SSE para asignacion fallida ID {}: {}", asignacionId, notifEx.message)
            }

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

    @Async
    override fun importarYEstandarizarTemplateAsincronicamente(
        targetRepoName: String,
        sourceRepoUrl: String,
    ) {
        logger.info("Iniciando importación y estandarización asincrónica del template '{}' desde '{}'", targetRepoName, sourceRepoUrl)
        try {
            // 1. Replicar todos los archivos del repositorio origen
            gitHubRepoService.importRepository(
                targetRepoName = targetRepoName,
                sourceRepoUrl = sourceRepoUrl,
            )

            // 2. Configurar formalmente como template vía PATCH (is_template = true)
            gitHubRepoService.setTemplateStatus(
                repoName = targetRepoName,
                isTemplate = true,
            )

            // 3. Pipeline de Verificación y Blindaje Inicial (marca templateAprobado = true y lo guarda en BD)
            templateStandardizationService?.asegurarTemplateEstandarizado(targetRepoName)

            logger.info("Template '{}' importado y estandarizado exitosamente en background", targetRepoName)
            try {
                sseNotificationService?.notificarTemplatesActualizados(targetRepoName)
            } catch (notifEx: Exception) {
                logger.warn("Error enviando notificación SSE para template listo '{}': {}", targetRepoName, notifEx.message)
            }
        } catch (e: Exception) {
            logger.error("Error crítico importando y estandarizando el template '{}'. Ejecutando rollback...", targetRepoName, e)
            try {
                val repo = repositorioRepository?.findByNombreIgnoreCase(targetRepoName)
                if (repo != null) {
                    repositorioRepository.delete(repo)
                    logger.info("Repositorio template fallido '{}' eliminado físicamente de la base de datos.", targetRepoName)
                }
            } catch (dbEx: Exception) {
                logger.warn("Error eliminando de la base de datos al template fallido '{}': {}", targetRepoName, dbEx.message)
            }

            try {
                gitHubRepoService.deleteRepository(targetRepoName)
            } catch (ghEx: Exception) {
                logger.warn("Error eliminando de GitHub el repositorio template fallido '{}': {}", targetRepoName, ghEx.message)
            }

            try {
                sseNotificationService?.notificarTemplatesActualizados(targetRepoName)
            } catch (notifEx: Exception) {
                logger.warn("Error enviando notificación SSE para template fallido '{}': {}", targetRepoName, notifEx.message)
            }
        }
    }
}
