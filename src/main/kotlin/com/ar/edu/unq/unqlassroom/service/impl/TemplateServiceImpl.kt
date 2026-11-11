package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.exception.GitHubNotFoundException
import com.ar.edu.unq.unqlassroom.exception.RepositorioExternoNoEncontradoException
import com.ar.edu.unq.unqlassroom.exception.RepositorioExternoPrivadoException
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.ar.edu.unq.unqlassroom.service.AsyncService
import com.ar.edu.unq.unqlassroom.service.TemplateService
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import com.ar.edu.unq.unqlassroom.util.ejecutarDespuesDeCommit
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TemplateServiceImpl(
    private val usuarioService: UsuarioService,
    private val gitHubRepoService: GitHubRepoService,
    private val repositorioRepository: RepositorioRepository,
    private val asyncService: AsyncService,
) : TemplateService {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun crearTemplateRepository(
        name: String,
        description: String?,
        solicitanteUsername: String,
        repoAImportar: String?,
    ): Repositorio {
        usuarioService.obtenerDocente(solicitanteUsername)

        val cleanName = name.trim()
        val cleanRepoAImportar = repoAImportar?.trim()?.takeIf { it.isNotEmpty() }

        logger.info("crearTemplateRepository llamado para '{}', repoAImportar: '{}'", cleanName, cleanRepoAImportar)

        if (cleanRepoAImportar.isNullOrBlank()) {
            logger.info("No se especificó repo a importar. Creando template en blanco '{}'", cleanName)
            val repoResponse = gitHubRepoService.createTemplateRepository(
                name = cleanName,
                description = description,
            )

            val repositorio = Repositorio(
                nombre = repoResponse.name,
                htmlUrl = repoResponse.htmlUrl,
                esTemplate = true,
                descripcion = repoResponse.description,
                githubRepoId = repoResponse.id.takeIf { it > 0 },
                templateAprobado = false,
            )

            return repositorioRepository.save(repositorio)
        }

        // 1. Validar sincrónicamente formato de URL y que el repositorio origen exista y sea público
        val (sourceOwner, sourceRepo) = gitHubRepoService.parsearOwnerYRepo(cleanRepoAImportar)
        val repoDetails = try {
            gitHubRepoService.getPublicRepoDetails(sourceOwner, sourceRepo)
        } catch (_: GitHubNotFoundException) {
            throw RepositorioExternoNoEncontradoException(sourceOwner, sourceRepo)
        }

        if (repoDetails.private) {
            throw RepositorioExternoPrivadoException(sourceOwner, sourceRepo)
        }

        logger.info("Iniciando creación de repo base '{}' para importar asincrónicamente desde '{}'", cleanName, cleanRepoAImportar)

        // 2. Crear el repositorio en la organización con autoInit = true para tener la rama base lista
        val repoResponse = gitHubRepoService.createOrgRepository(
            name = cleanName,
            description = description,
            private = true,
            autoInit = true,
            isTemplate = false,
        )

        // 3. Persistir en BD con esTemplate = true y templateAprobado = false (en proceso de creación/importación)
        val repositorio = Repositorio(
            nombre = repoResponse.name.ifBlank { cleanName },
            htmlUrl = repoResponse.htmlUrl,
            esTemplate = true,
            descripcion = description ?: repoResponse.description,
            githubRepoId = repoResponse.id.takeIf { it > 0 },
            templateAprobado = false,
        )
        val repoGuardado = repositorioRepository.save(repositorio)

        // 4. Lanzar importación, conversión a template y estandarización en background después del commit
        ejecutarDespuesDeCommit {
            asyncService.importarYEstandarizarTemplateAsincronicamente(
                targetRepoName = cleanName,
                sourceRepoUrl = cleanRepoAImportar,
            )
        }

        return repoGuardado
    }

    @Transactional(readOnly = true)
    override fun listarTemplates(solicitanteUsername: String): List<Repositorio> {
        usuarioService.obtenerDocente(solicitanteUsername)
        return repositorioRepository.findByEsTemplateTrueAndEliminadoFalseOrderByNombreAsc()
    }
}
