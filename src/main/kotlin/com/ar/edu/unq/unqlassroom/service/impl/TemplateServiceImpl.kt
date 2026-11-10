package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.ar.edu.unq.unqlassroom.service.TemplateService
import com.ar.edu.unq.unqlassroom.service.TemplateStandardizationService
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TemplateServiceImpl(
    private val usuarioService: UsuarioService,
    private val gitHubRepoService: GitHubRepoService,
    private val repositorioRepository: RepositorioRepository,
    private val templateStandardizationService: TemplateStandardizationService,
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
            )

            return repositorioRepository.save(repositorio)
        }

        logger.info("Iniciando creación e importación de template '{}' desde '{}'", cleanName, cleanRepoAImportar)

        // 1. Crear el repositorio en la organización con autoInit = true para tener la rama base lista
        val repoResponse = gitHubRepoService.createOrgRepository(
            name = cleanName,
            description = description,
            private = true,
            autoInit = true,
            isTemplate = false,
        )

        // 2. Replicar todos los archivos del repositorio origen
        gitHubRepoService.importRepository(
            targetRepoName = cleanName,
            sourceRepoUrlOrSlug = cleanRepoAImportar,
            fallbackOwner = solicitanteUsername,
        )

        // 3. Configurar formalmente como template vía PATCH (is_template = true)
        gitHubRepoService.setTemplateStatus(
            repoName = cleanName,
            isTemplate = true,
        )

        // 4. Pipeline de Verificación y Blindaje Inicial
        val repoEstandarizado = templateStandardizationService.asegurarTemplateEstandarizado(cleanName)
        if (repoResponse.id > 0) {
            repoEstandarizado.githubRepoId = repoResponse.id
        }
        if (description != null) {
            repoEstandarizado.descripcion = description
        }

        return repositorioRepository.save(repoEstandarizado)
    }

    @Transactional(readOnly = true)
    override fun listarTemplates(solicitanteUsername: String): List<Repositorio> {
        usuarioService.obtenerDocente(solicitanteUsername)

        return repositorioRepository.findByEsTemplateTrueAndEliminadoFalseOrderByNombreAsc()
    }
}
