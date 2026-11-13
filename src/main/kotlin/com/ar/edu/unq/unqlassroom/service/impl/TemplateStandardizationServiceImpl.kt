package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.integration.github.config.GitHubAppProperties
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.LenguajeProyecto
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.ar.edu.unq.unqlassroom.service.TemplateStandardizationService
import com.ar.edu.unq.unqlassroom.service.standardization.DotnetTemplateHandler
import com.ar.edu.unq.unqlassroom.service.standardization.JavaBuildPatcher
import com.ar.edu.unq.unqlassroom.service.standardization.JavaTemplateHandler
import com.ar.edu.unq.unqlassroom.service.standardization.NodeTemplateHandler
import com.ar.edu.unq.unqlassroom.service.standardization.PythonTemplateHandler
import com.ar.edu.unq.unqlassroom.service.standardization.StandardizationResourceLoader
import com.ar.edu.unq.unqlassroom.service.standardization.TemplateRepoContext
import com.ar.edu.unq.unqlassroom.service.standardization.WorkflowCiBuilder
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TemplateStandardizationServiceImpl(
    private val gitHubRepoService: GitHubRepoService,
    private val repositorioRepository: RepositorioRepository,
    private val gitHubAppProperties: GitHubAppProperties,
    private val javaTemplateHandler: JavaTemplateHandler,
    private val pythonTemplateHandler: PythonTemplateHandler,
    private val nodeTemplateHandler: NodeTemplateHandler,
    private val dotnetTemplateHandler: DotnetTemplateHandler,
    private val javaBuildPatcher: JavaBuildPatcher,
    private val workflowCiBuilder: WorkflowCiBuilder,
) : TemplateStandardizationService {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun asegurarTemplateEstandarizado(templateRepoName: String): Repositorio {
        val cleanName = templateRepoName.trim()
        require(cleanName.isNotEmpty()) { "El nombre del repositorio template no puede estar vacío" }

        val org = gitHubAppProperties.organization?.trim()?.takeIf { it.isNotEmpty() } ?: "UNQlassroom-cursos"
        val defaultHtmlUrl = "https://github.com/$org/$cleanName"

        val repositorio = repositorioRepository.findByNombreIgnoreCase(cleanName)?.apply {
            if (!this.esTemplate) {
                this.marcarComoTemplate()
            }
        } ?: Repositorio(
            nombre = cleanName,
            htmlUrl = defaultHtmlUrl,
            esTemplate = true,
        )

        val ultimoCommitRemoto = gitHubRepoService.getUltimoCommit(cleanName)
        val currentCommitSha = ultimoCommitRemoto?.sha?.takeIf { it.isNotBlank() }

        if (repositorio.estaTemplateAprobadoConCommit(currentCommitSha)) {
            logger.info(
                "Cache HIT para el template '{}': commit '{}' ya se encuentra aprobado. Se omite escaneo de archivos.",
                cleanName,
                currentCommitSha
            )
            return if (repositorio.id == null) repositorioRepository.save(repositorio) else repositorio
        }

        logger.info(
            "Cache MISS para el template '{}' (commit remoto: '{}', commit guardado: '{}', aprobado: {}). Iniciando estandarización...",
            cleanName,
            currentCommitSha,
            repositorio.ultimoCommitSha,
            repositorio.templateAprobado
        )

        val treeResponse = if (!currentCommitSha.isNullOrBlank()) {
            gitHubRepoService.getRepositoryTree(cleanName, currentCommitSha, recursive = true)
        } else {
            null
        }

        val tieneArbol = treeResponse != null
        val archivosEnArbol: Map<String, String> = treeResponse?.tree
            ?.filter { it.type == "blob" || it.type.isBlank() }
            ?.associate { it.path.trimStart('/') to it.sha }
            ?: emptyMap()

        val ctx = TemplateRepoContext(
            repoName = cleanName,
            tieneArbol = tieneArbol,
            archivosEnArbol = archivosEnArbol,
            gitHubRepoService = gitHubRepoService,
        )

        // 1. Detección inteligente del lenguaje del template
        val lenguaje = detectarLenguaje(archivosEnArbol.keys)
        logger.info("Lenguaje detectado para el template '{}': {} ({})", cleanName, lenguaje.nombreLegible, lenguaje.name)

        when (lenguaje) {
            LenguajeProyecto.JAVA_MAVEN, LenguajeProyecto.JAVA_GRADLE -> {
                javaTemplateHandler.estandarizar(ctx, lenguaje)
            }
            LenguajeProyecto.PYTHON -> {
                pythonTemplateHandler.estandarizar(ctx)
            }
            LenguajeProyecto.NODE_JS -> {
                nodeTemplateHandler.estandarizar(ctx)
            }
            LenguajeProyecto.DOTNET -> {
                dotnetTemplateHandler.estandarizar(ctx)
            }
            LenguajeProyecto.GENERICO -> {
                logger.info(
                    "El template '{}' contiene archivos pero no corresponde a los stacks soportados. Se omite inyección forzada de dependencias.",
                    cleanName
                )
            }
        }

        // 2. Actualizar caché en BD con el último commit SHA resultante y marcar templateAprobado = true
        val shaFinal = ctx.ultimoShaGenerado?.takeIf { it.isNotBlank() }
            ?: currentCommitSha
            ?: gitHubRepoService.getUltimoCommit(cleanName)?.sha?.takeIf { it.isNotBlank() }
            ?: "standardized"

        repositorio.aprobarTemplate(shaFinal)
        val guardado = repositorioRepository.save(repositorio)
        logger.info(
            "Template '{}' estandarizado y aprobado exitosamente con commit SHA '{}'",
            cleanName,
            guardado.ultimoCommitSha
        )
        return guardado
    }

    override fun analizarYParchearPom(pomContent: String): String? =
        javaBuildPatcher.analizarYParchearPom(pomContent)

    override fun analizarYParchearGradle(gradleContent: String, esKotlinDsl: Boolean): String? =
        javaBuildPatcher.analizarYParchearGradle(gradleContent, esKotlinDsl)

    override fun detectarLenguaje(archivos: Collection<String>): LenguajeProyecto {
        val lowerPaths = archivos.map { it.lowercase().trim().trimStart('/') }
        return when {
            lowerPaths.any { it == "pom.xml" || it.endsWith("/pom.xml") } ->
                LenguajeProyecto.JAVA_MAVEN

            lowerPaths.any {
                it == "build.gradle" || it == "build.gradle.kts" ||
                    it.endsWith("/build.gradle") || it.endsWith("/build.gradle.kts") ||
                    it == "settings.gradle" || it == "settings.gradle.kts"
            } ->
                LenguajeProyecto.JAVA_GRADLE

            lowerPaths.any {
                it == "pyproject.toml" || it == "requirements.txt" || it == "pipfile" ||
                    it == "setup.py" || it == "setup.cfg" || it == "tox.ini" ||
                    it.endsWith(".py")
            } ->
                LenguajeProyecto.PYTHON

            lowerPaths.any {
                it == "package.json" || it.endsWith("/package.json") ||
                    it.endsWith(".ts") || it.endsWith(".tsx") || it.endsWith(".js") || it.endsWith(".jsx")
            } ->
                LenguajeProyecto.NODE_JS

            lowerPaths.any {
                it.endsWith(".csproj") || it.endsWith(".sln") || it.endsWith(".fsproj") || it.endsWith(".cs")
            } ->
                LenguajeProyecto.DOTNET

            else -> {
                val esVacioOBoilerplate = lowerPaths.isEmpty() || lowerPaths.all { path ->
                    val filename = path.substringAfterLast('/')
                    filename in listOf("readme.md", ".gitignore", "license", "license.md", "license.txt")
                }
                if (esVacioOBoilerplate) LenguajeProyecto.JAVA_MAVEN else LenguajeProyecto.GENERICO
            }
        }
    }

    override fun construirWorkflowCI(): String = construirWorkflowCI(LenguajeProyecto.JAVA_MAVEN)

    override fun construirWorkflowCI(lenguaje: LenguajeProyecto): String =
        workflowCiBuilder.build(lenguaje)

    companion object {
        val DEFAULT_POM_XML: String by lazy {
            StandardizationResourceLoader.load("java/pom.xml")
        }
    }
}
