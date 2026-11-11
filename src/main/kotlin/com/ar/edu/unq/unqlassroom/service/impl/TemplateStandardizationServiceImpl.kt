package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.integration.github.config.GitHubAppProperties
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.LenguajeProyecto
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.ar.edu.unq.unqlassroom.service.TemplateStandardizationService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TemplateStandardizationServiceImpl(
    private val gitHubRepoService: GitHubRepoService,
    private val repositorioRepository: RepositorioRepository,
    private val gitHubAppProperties: GitHubAppProperties,
    @Value("\${github.webhook.public-url:}")
    private val webhookPublicUrl: String = "",
) : TemplateStandardizationService {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val targetWebhookUrl: String
        get() = webhookPublicUrl.trim()

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

        var ultimoShaGenerado: String? = currentCommitSha

        fun obtenerArchivo(path: String) =
            if (!tieneArbol || archivosEnArbol.containsKey(path)) {
                gitHubRepoService.getFileContent(cleanName, path)
            } else {
                null
            }

        fun existeArchivo(path: String): Boolean =
            if (tieneArbol) {
                archivosEnArbol.containsKey(path)
            } else {
                gitHubRepoService.getFileContent(cleanName, path) != null
            }

        fun escribirArchivo(path: String, content: String, message: String, knownSha: String? = null) {
            val targetSha = knownSha ?: archivosEnArbol[path]
            val response = gitHubRepoService.createOrUpdateFile(
                repoName = cleanName,
                path = path,
                content = content,
                message = message,
                sha = targetSha,
            )
            val newCommitSha = response.commit?.sha?.takeIf { it.isNotBlank() }
            if (newCommitSha != null) {
                ultimoShaGenerado = newCommitSha
            }
        }

        // 1. Detección inteligente del lenguaje del template
        val lenguaje = detectarLenguaje(archivosEnArbol.keys)
        logger.info("Lenguaje detectado para el template '{}': {} ({})", cleanName, lenguaje.nombreLegible, lenguaje.name)

        when (lenguaje) {
            LenguajeProyecto.JAVA_MAVEN, LenguajeProyecto.JAVA_GRADLE -> {
                // Inspección y parcheo del gestor de dependencias (pom.xml / build.gradle / build.gradle.kts)
                val pomFile = obtenerArchivo("pom.xml")
                val gradleKtsFile = if (pomFile == null) obtenerArchivo("build.gradle.kts") else null
                val gradleGroovyFile = if (pomFile == null && gradleKtsFile == null) obtenerArchivo("build.gradle") else null

                val esProyectoGradle = gradleKtsFile != null || gradleGroovyFile != null

                when {
                    pomFile != null -> {
                        val pomContent = pomFile.decodedContent()
                        val pomParcheado = analizarYParchearPom(pomContent)
                        if (pomParcheado != null) {
                            logger.info("Parcheando pom.xml en template '{}' para asegurar JaCoCo y Checkstyle", cleanName)
                            escribirArchivo(
                                path = "pom.xml",
                                content = pomParcheado,
                                message = "chore(ci): asegurar dependencias y plugins de JaCoCo y Checkstyle en pom.xml",
                                knownSha = pomFile.sha,
                            )
                        }
                    }
                    gradleKtsFile != null -> {
                        val gradleContent = gradleKtsFile.decodedContent()
                        val gradleParcheado = analizarYParchearGradle(gradleContent, esKotlinDsl = true)
                        if (gradleParcheado != null) {
                            logger.info("Parcheando build.gradle.kts en template '{}' para asegurar JaCoCo y Checkstyle", cleanName)
                            escribirArchivo(
                                path = "build.gradle.kts",
                                content = gradleParcheado,
                                message = "chore(ci): asegurar configuración de JaCoCo y Checkstyle en build.gradle.kts",
                                knownSha = gradleKtsFile.sha,
                            )
                        }
                    }
                    gradleGroovyFile != null -> {
                        val gradleContent = gradleGroovyFile.decodedContent()
                        val gradleParcheado = analizarYParchearGradle(gradleContent, esKotlinDsl = false)
                        if (gradleParcheado != null) {
                            logger.info("Parcheando build.gradle en template '{}' para asegurar JaCoCo y Checkstyle", cleanName)
                            escribirArchivo(
                                path = "build.gradle",
                                content = gradleParcheado,
                                message = "chore(ci): asegurar configuración de JaCoCo y Checkstyle en build.gradle",
                                knownSha = gradleGroovyFile.sha,
                            )
                        }
                    }
                    else -> {
                        logger.info("El template '{}' no posee pom.xml ni build.gradle; inyectando pom.xml base", cleanName)
                        escribirArchivo(
                            path = "pom.xml",
                            content = DEFAULT_POM_XML,
                            message = "chore(ci): agregar pom.xml base con JaCoCo, Checkstyle y JUnit 5",
                        )
                    }
                }

                // Asegurar Wrapper (Maven Wrapper o Gradle Wrapper)
                val tieneMavenWrapperCompleto = existeArchivo("mvnw") &&
                    existeArchivo("mvnw.cmd") &&
                    existeArchivo(".mvn/wrapper/maven-wrapper.properties")

                val tieneGradleWrapperCompleto = existeArchivo("gradlew") &&
                    existeArchivo("gradlew.bat") &&
                    existeArchivo("gradle/wrapper/gradle-wrapper.properties")

                if (!tieneMavenWrapperCompleto && !tieneGradleWrapperCompleto) {
                    if (esProyectoGradle) {
                        if (!existeArchivo("gradle/wrapper/gradle-wrapper.properties")) {
                            escribirArchivo(
                                path = "gradle/wrapper/gradle-wrapper.properties",
                                content = GRADLE_WRAPPER_PROPERTIES,
                                message = "chore(ci): agregar gradle-wrapper.properties",
                            )
                        }
                        if (!existeArchivo("gradlew")) {
                            escribirArchivo(
                                path = "gradlew",
                                content = GRADLEW_UNIX_SCRIPT,
                                message = "chore(ci): agregar script gradlew",
                            )
                        }
                        if (!existeArchivo("gradlew.bat")) {
                            escribirArchivo(
                                path = "gradlew.bat",
                                content = GRADLEW_WINDOWS_SCRIPT,
                                message = "chore(ci): agregar script gradlew.bat",
                            )
                        }
                    } else {
                        if (!existeArchivo(".mvn/wrapper/maven-wrapper.properties")) {
                            escribirArchivo(
                                path = ".mvn/wrapper/maven-wrapper.properties",
                                content = MAVEN_WRAPPER_PROPERTIES,
                                message = "chore(ci): agregar maven-wrapper.properties",
                            )
                        }
                        if (!existeArchivo("mvnw")) {
                            escribirArchivo(
                                path = "mvnw",
                                content = MVNW_UNIX_SCRIPT,
                                message = "chore(ci): agregar script mvnw",
                            )
                        }
                        if (!existeArchivo("mvnw.cmd")) {
                            escribirArchivo(
                                path = "mvnw.cmd",
                                content = MVNW_WINDOWS_SCRIPT,
                                message = "chore(ci): agregar script mvnw.cmd",
                            )
                        }
                    }
                }

                // Estructura mínima: clase base en src/main/java y test de rescate en src/test/java
                val tieneTestsEnArbol = if (tieneArbol) {
                    archivosEnArbol.keys.any { path ->
                        path.startsWith("src/test/") && (path.endsWith(".java") || path.endsWith(".kt"))
                    }
                } else {
                    existeArchivo("src/test/java/SanityTest.java")
                }

                if (!tieneTestsEnArbol) {
                    if (!existeArchivo("src/main/java/Sanity.java")) {
                        logger.info("El template '{}' no posee tests en src/test; inyectando clase de rescate Sanity.java", cleanName)
                        escribirArchivo(
                            path = "src/main/java/Sanity.java",
                            content = RESCUE_SANITY_JAVA,
                            message = "feat(ci): agregar clase de rescate por defecto en src/main/java/Sanity.java",
                        )
                    }

                    logger.info("El template '{}' no posee tests en src/test; inyectando test de rescate SanityTest.java", cleanName)
                    escribirArchivo(
                        path = "src/test/java/SanityTest.java",
                        content = RESCUE_SANITY_TEST_JAVA,
                        message = "test(ci): agregar test de rescate por defecto en src/test/java/SanityTest.java",
                    )
                }

                // Inyección del workflow de CI para Java
                val ciWorkflowPath = ".github/workflows/ci.yml"
                val existingCiSha = archivosEnArbol[ciWorkflowPath]
                    ?: if (!tieneArbol) gitHubRepoService.getFileContent(cleanName, ciWorkflowPath)?.sha else null

                logger.info("Inyectando/pisando obligatoriamente {} en el template '{}' ({})", ciWorkflowPath, cleanName, lenguaje.nombreLegible)
                escribirArchivo(
                    path = ciWorkflowPath,
                    content = construirWorkflowCI(lenguaje),
                    message = "ci: inyectar workflow estandarizado de calidad UNQlassroom para Java",
                    knownSha = existingCiSha,
                )
            }
            LenguajeProyecto.PYTHON -> {
                val tieneTests = if (tieneArbol) {
                    archivosEnArbol.keys.any { path ->
                        val p = path.lowercase()
                        (p.startsWith("test/") || p.startsWith("tests/") || p.contains("/test/") || p.contains("/tests/")) ||
                            p.substringAfterLast('/').let { it.startsWith("test_") || it.endsWith("_test.py") }
                    }
                } else {
                    existeArchivo("tests/test_sanity.py")
                }

                if (!tieneTests && !existeArchivo("tests/test_sanity.py")) {
                    logger.info("El template '{}' (Python) no posee tests; inyectando tests/test_sanity.py", cleanName)
                    escribirArchivo(
                        path = "tests/test_sanity.py",
                        content = RESCUE_SANITY_PYTHON,
                        message = "test(ci): agregar test de rescate por defecto en tests/test_sanity.py",
                    )
                }

                val ciWorkflowPath = ".github/workflows/ci.yml"
                val existingCiSha = archivosEnArbol[ciWorkflowPath]
                    ?: if (!tieneArbol) gitHubRepoService.getFileContent(cleanName, ciWorkflowPath)?.sha else null

                logger.info("Inyectando/pisando obligatoriamente {} en el template '{}' (Python)", ciWorkflowPath, cleanName)
                escribirArchivo(
                    path = ciWorkflowPath,
                    content = construirWorkflowCI(LenguajeProyecto.PYTHON),
                    message = "ci: inyectar workflow estandarizado de calidad UNQlassroom para Python (Ruff & Pytest)",
                    knownSha = existingCiSha,
                )
            }
            LenguajeProyecto.NODE_JS -> {
                val tieneTests = if (tieneArbol) {
                    archivosEnArbol.keys.any { path ->
                        val p = path.lowercase()
                        p.endsWith(".test.js") || p.endsWith(".spec.js") ||
                            p.endsWith(".test.ts") || p.endsWith(".spec.ts") ||
                            p.endsWith(".test.jsx") || p.endsWith(".test.tsx") ||
                            p.startsWith("test/") || p.startsWith("tests/") || p.startsWith("__tests__/")
                    }
                } else {
                    existeArchivo("test/sanity.test.js")
                }

                if (!tieneTests && !existeArchivo("test/sanity.test.js")) {
                    logger.info("El template '{}' (Node.js) no posee tests; inyectando test/sanity.test.js", cleanName)
                    escribirArchivo(
                        path = "test/sanity.test.js",
                        content = RESCUE_SANITY_NODE,
                        message = "test(ci): agregar test de rescate por defecto en test/sanity.test.js",
                    )
                }

                val ciWorkflowPath = ".github/workflows/ci.yml"
                val existingCiSha = archivosEnArbol[ciWorkflowPath]
                    ?: if (!tieneArbol) gitHubRepoService.getFileContent(cleanName, ciWorkflowPath)?.sha else null

                logger.info("Inyectando/pisando obligatoriamente {} en el template '{}' (Node.js)", ciWorkflowPath, cleanName)
                escribirArchivo(
                    path = ciWorkflowPath,
                    content = construirWorkflowCI(LenguajeProyecto.NODE_JS),
                    message = "ci: inyectar workflow estandarizado de calidad UNQlassroom para Node.js (ESLint & Tests)",
                    knownSha = existingCiSha,
                )
            }
            LenguajeProyecto.DOTNET -> {
                val ciWorkflowPath = ".github/workflows/ci.yml"
                val existingCiSha = archivosEnArbol[ciWorkflowPath]
                    ?: if (!tieneArbol) gitHubRepoService.getFileContent(cleanName, ciWorkflowPath)?.sha else null

                logger.info("Inyectando/pisando obligatoriamente {} en el template '{}' (.NET)", ciWorkflowPath, cleanName)
                escribirArchivo(
                    path = ciWorkflowPath,
                    content = construirWorkflowCI(LenguajeProyecto.DOTNET),
                    message = "ci: inyectar workflow estandarizado de calidad UNQlassroom para .NET (Roslyn & Coverlet)",
                    knownSha = existingCiSha,
                )
            }
            LenguajeProyecto.GENERICO -> {
                logger.info("El template '{}' contiene archivos pero no corresponde a los stacks soportados. Se omite inyección forzada de dependencias.", cleanName)
            }
        }

        // 5. Actualizar caché en BD con el último commit SHA resultante y marcar templateAprobado = true
        val shaFinal = ultimoShaGenerado?.takeIf { it.isNotBlank() }
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

    override fun analizarYParchearPom(pomContent: String): String? {
        if (pomContent.isBlank()) {
            return DEFAULT_POM_XML
        }

        val tieneJacoco = pomContent.contains("jacoco-maven-plugin")
        val tieneCheckstyle = pomContent.contains("maven-checkstyle-plugin")
        val tieneDependenciaTest = pomContent.contains("junit-jupiter") ||
            pomContent.contains("spring-boot-starter-test") ||
            pomContent.contains("kotlin-test") ||
            pomContent.contains("<artifactId>junit</artifactId>") ||
            pomContent.contains("testng")

        if (tieneJacoco && tieneCheckstyle && tieneDependenciaTest) {
            return null
        }

        var resultado = pomContent

        if (!tieneDependenciaTest) {
            val junitDependencySnippet = """
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>""".trimIndent()

            resultado = if (resultado.contains("</dependencies>")) {
                resultado.replaceFirst(
                    "</dependencies>",
                    "    $junitDependencySnippet\n    </dependencies>"
                )
            } else if (resultado.contains("</project>")) {
                val dependenciesBlock = """
    <dependencies>
$junitDependencySnippet
    </dependencies>
</project>""".trimIndent()
                resultado.replaceFirst("</project>", dependenciesBlock)
            } else {
                resultado
            }
        }

        val pluginsFaltantes = mutableListOf<String>()
        if (!tieneJacoco) {
            pluginsFaltantes.add(JACOCO_MAVEN_PLUGIN_SNIPPET)
        }
        if (!tieneCheckstyle) {
            pluginsFaltantes.add(CHECKSTYLE_MAVEN_PLUGIN_SNIPPET)
        }

        if (!tieneDependenciaTest && !resultado.contains("maven-surefire-plugin")) {
            pluginsFaltantes.add(SUREFIRE_MAVEN_PLUGIN_SNIPPET)
        }

        if (pluginsFaltantes.isNotEmpty()) {
            val bloquePluginsAInyectar = pluginsFaltantes.joinToString("\n")
            resultado = inyectarPluginsEnPom(resultado, bloquePluginsAInyectar)
        }

        return if (resultado != pomContent) resultado else null
    }

    private fun inyectarPluginsEnPom(pom: String, pluginsXml: String): String {
        val buildCloseIdx = pom.lastIndexOf("</build>")
        if (buildCloseIdx >= 0) {
            val buildStartIdx = pom.lastIndexOf("<build", buildCloseIdx)
            if (buildStartIdx >= 0) {
                val buildSection = pom.substring(buildStartIdx, buildCloseIdx)
                val sectionSinPluginManagement = buildSection.replace(
                    Regex("<pluginManagement>[\\s\\S]*?</pluginManagement>"),
                    ""
                )
                if (sectionSinPluginManagement.contains("</plugins>")) {
                    val lastPluginsCloseInBuild = pom.lastIndexOf("</plugins>", buildCloseIdx)
                    if (lastPluginsCloseInBuild > buildStartIdx) {
                        return pom.substring(0, lastPluginsCloseInBuild) +
                            pluginsXml + "\n        " +
                            pom.substring(lastPluginsCloseInBuild)
                    }
                }
                val nuevoBloquePlugins = "    <plugins>\n$pluginsXml\n        </plugins>\n    "
                return pom.substring(0, buildCloseIdx) + nuevoBloquePlugins + pom.substring(buildCloseIdx)
            }
        }

        val projectCloseIdx = pom.lastIndexOf("</project>")
        if (projectCloseIdx >= 0) {
            val bloqueBuild = """    <build>
        <plugins>
$pluginsXml
        </plugins>
    </build>
"""
            return pom.substring(0, projectCloseIdx) + bloqueBuild + pom.substring(projectCloseIdx)
        }

        return "$pom\n<build>\n<plugins>\n$pluginsXml\n</plugins>\n</build>"
    }

    override fun analizarYParchearGradle(gradleContent: String, esKotlinDsl: Boolean): String? {
        val tieneJacoco = Regex("""\bjacoco\b""", RegexOption.IGNORE_CASE).containsMatchIn(gradleContent)
        val tieneCheckstyle = Regex("""\bcheckstyle\b""", RegexOption.IGNORE_CASE).containsMatchIn(gradleContent)

        if (tieneJacoco && tieneCheckstyle) {
            return null
        }

        val bloques = mutableListOf<String>()
        if (esKotlinDsl) {
            if (!tieneJacoco) {
                bloques.add(
                    """
                    apply(plugin = "jacoco")
                    tasks.withType<JacocoReport> {
                        reports {
                            xml.required.set(true)
                            html.required.set(true)
                        }
                    }
                    """.trimIndent()
                )
            }
            if (!tieneCheckstyle) {
                bloques.add(
                    """
                    apply(plugin = "checkstyle")
                    configure<CheckstyleExtension> {
                        toolVersion = "10.12.4"
                        isIgnoreFailures = true
                    }
                    """.trimIndent()
                )
            }
        } else {
            if (!tieneJacoco) {
                bloques.add(
                    """
                    apply plugin: 'jacoco'
                    jacocoTestReport {
                        reports {
                            xml.required = true
                            html.required = true
                        }
                    }
                    """.trimIndent()
                )
            }
            if (!tieneCheckstyle) {
                bloques.add(
                    """
                    apply plugin: 'checkstyle'
                    checkstyle {
                        toolVersion = '10.12.4'
                        ignoreFailures = true
                    }
                    """.trimIndent()
                )
            }
        }

        val separador = if (gradleContent.endsWith("\n")) "\n" else "\n\n"
        return gradleContent + separador + "// --- UNQlassroom Quality Tools ---\n" + bloques.joinToString("\n\n") + "\n"
    }

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

    override fun construirWorkflowCI(lenguaje: LenguajeProyecto): String {
        return when (lenguaje) {
            LenguajeProyecto.JAVA_MAVEN, LenguajeProyecto.JAVA_GRADLE ->
                construirWorkflowCIJava(esGradle = (lenguaje == LenguajeProyecto.JAVA_GRADLE))
            LenguajeProyecto.PYTHON ->
                construirWorkflowCIPython()
            LenguajeProyecto.NODE_JS ->
                construirWorkflowCINode()
            LenguajeProyecto.DOTNET ->
                construirWorkflowCIDotnet()
            LenguajeProyecto.GENERICO ->
                construirWorkflowCIJava(esGradle = false)
        }
    }

    private fun construirWorkflowCIJava(esGradle: Boolean = false): String {
        val targetUrl = targetWebhookUrl
        val linterRun = if (esGradle) {
            """
          chmod +x gradlew || true
          ./gradlew checkstyleMain checkstyleTest || true
            """.trimIndent()
        } else {
            """
          chmod +x mvnw || true
          ./mvnw org.apache.maven.plugins:maven-checkstyle-plugin:3.3.1:checkstyle \
            -Dcheckstyle.config.location=google_checks.xml \
            -Dcheckstyle.violationSeverity=warning || true
            """.trimIndent()
        }

        val testRun = if (esGradle) {
            """
          # Corre todos los tests generando reportes y JaCoCo aun si algún test falla
          ./gradlew test jacocoTestReport --continue || true
            """.trimIndent()
        } else {
            """
          # Corre todos los tests generando reportes de Surefire y el XML de JaCoCo aun si algún test falla
          ./mvnw test jacoco:report -Dmaven.test.failure.ignore=true || true
            """.trimIndent()
        }

        return """
name: CI Linter & Tests (Java)

on:
  push:
    branches: [ "main", "master", "develop" ]
  pull_request:
    branches: [ "main", "master", "develop" ]

jobs:
  evaluar-calidad:
    runs-on: ubuntu-latest

    steps:
      - name: 1. Descargar repositorio
        uses: actions/checkout@v4

      - name: 2. Configurar Java JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          cache: '${if (esGradle) "gradle" else "maven"}'

      - name: 3. Ejecutar Linter (Checkstyle)
        run: |
$linterRun

      - name: 4. Enviar reporte de Linter a UNQlassroom
        if: always()
        run: |
          REPORT_FILE="target/checkstyle-result.xml"
          if [ ! -f "${'$'}REPORT_FILE" ]; then
            if [ -f "build/reports/checkstyle/main.xml" ]; then
              REPORT_FILE="build/reports/checkstyle/main.xml"
            else
              echo '<?xml version="1.0" encoding="UTF-8"?><checkstyle version="10.0"></checkstyle>' > empty-checkstyle.xml
              REPORT_FILE="empty-checkstyle.xml"
            fi
          fi
          XML_CONTENT=${'$'}(jq -Rs . < "${'$'}REPORT_FILE")

          PAYLOAD=${'$'}(jq -n \
            --arg repo "${'$'}{{ github.event.repository.name }}" \
            --arg sha "${'$'}{{ github.event.pull_request.head.sha || github.sha }}" \
            --arg author "${'$'}{{ github.actor }}" \
            --arg run_id "${'$'}{{ github.run_id }}" \
            --arg tool "checkstyle" \
            --argjson xml "${'$'}XML_CONTENT" \
            '{
              repository: { name: ${'$'}repo },
              sha: ${'$'}sha,
              head_commit: { id: ${'$'}sha, author: { username: ${'$'}author } },
              run_id: (${'$'}run_id | tonumber),
              herramienta: ${'$'}tool,
              checkstyle_xml: ${'$'}xml
            }')

          curl -X POST "$targetUrl" \
            -H "Content-Type: application/json" \
            -H "X-GitHub-Event: linter_report" \
            -d "${'$'}PAYLOAD"

      - name: 5. Ejecutar Tests y Cobertura (JaCoCo)
        run: |
$testRun

      - name: 6. Enviar reporte de Cobertura, Tests y Líneas de Commit a UNQlassroom
        if: always()
        run: |
          # 1. Leer JaCoCo si existe
          JACOCO_FILE="target/site/jacoco/jacoco.xml"
          if [ ! -f "${'$'}JACOCO_FILE" ] && [ -f "build/reports/jacoco/test/jacocoTestReport.xml" ]; then
            JACOCO_FILE="build/reports/jacoco/test/jacocoTestReport.xml"
          fi
          if [ -f "${'$'}JACOCO_FILE" ]; then
            JACOCO_XML=${'$'}(jq -Rs . < "${'$'}JACOCO_FILE")
          else
            JACOCO_XML='""'
          fi

          # 2. Leer reportes de JUnit Surefire / Gradle si existen
          SUREFIRE_XML=""
          if compgen -G "target/surefire-reports/TEST-*.xml" > /dev/null; then
            SUREFIRE_XML="<testsuites>${'$'}(cat target/surefire-reports/TEST-*.xml)</testsuites>"
          elif compgen -G "build/test-results/test/TEST-*.xml" > /dev/null; then
            SUREFIRE_XML="<testsuites>${'$'}(cat build/test-results/test/TEST-*.xml)</testsuites>"
          fi
          JUNIT_XML=${'$'}(echo "${'$'}SUREFIRE_XML" | jq -Rs .)

          # 3. Calcular líneas agregadas y eliminadas del commit con Git
          PARENT=${'$'}(git rev-parse --verify HEAD~1 2>/dev/null || echo "")
          if [ -n "${'$'}PARENT" ]; then
            INSERTIONS=${'$'}(git diff --numstat HEAD~1 HEAD | awk '{s+=${'$'}1} END {print s+0}')
            DELETIONS=${'$'}(git diff --numstat HEAD~1 HEAD | awk '{s+=${'$'}2} END {print s+0}')
            FILES_CHANGED=${'$'}(git diff --numstat HEAD~1 HEAD | wc -l | tr -d ' ')
          else
            INSERTIONS=${'$'}(git diff --numstat 4b825dc642cb6eb9a060e54bf8d69288fbee4904 HEAD 2>/dev/null | awk '{s+=${'$'}1} END {print s+0}')
            DELETIONS=0
            FILES_CHANGED=${'$'}(git diff --numstat 4b825dc642cb6eb9a060e54bf8d69288fbee4904 HEAD 2>/dev/null | wc -l | tr -d ' ')
          fi
          TOTAL_LINES=${'$'}((INSERTIONS + DELETIONS))
          COMMIT_MSG=${'$'}(git log -1 --pretty=%B)

          # 4. Construir y enviar payload completo
          PAYLOAD_CI=${'$'}(jq -n \
            --arg repo "${'$'}{{ github.event.repository.name }}" \
            --arg sha "${'$'}{{ github.event.pull_request.head.sha || github.sha }}" \
            --arg author "${'$'}{{ github.actor }}" \
            --arg run_id "${'$'}{{ github.run_id }}" \
            --arg msg "${'$'}COMMIT_MSG" \
            --arg insertions "${'$'}INSERTIONS" \
            --arg deletions "${'$'}DELETIONS" \
            --arg total_lines "${'$'}TOTAL_LINES" \
            --arg files_changed "${'$'}FILES_CHANGED" \
            --argjson jacoco "${'$'}JACOCO_XML" \
            --argjson junit "${'$'}JUNIT_XML" \
            '{
              repository: { name: ${'$'}repo },
              sha: ${'$'}sha,
              head_commit: {
                id: ${'$'}sha,
                message: ${'$'}msg,
                author: { username: ${'$'}author },
                stats: {
                  additions: (${'$'}insertions | tonumber),
                  deletions: (${'$'}deletions | tonumber),
                  total: (${'$'}total_lines | tonumber),
                  files_changed: (${'$'}files_changed | tonumber)
                }
              },
              run_id: (${'$'}run_id | tonumber),
              jacoco_xml: ${'$'}jacoco,
              junit_xml: ${'$'}junit
            }')

          curl -X POST "$targetUrl" \
            -H "Content-Type: application/json" \
            -H "X-GitHub-Event: coverage_report" \
            -d "${'$'}PAYLOAD_CI"
""".trimIndent() + "\n"
    }

    private fun construirWorkflowCIPython(): String {
        val targetUrl = targetWebhookUrl
        return """
name: CI Linter & Tests (Python)

on:
  push:
    branches: [ "main", "master", "develop" ]
  pull_request:
    branches: [ "main", "master", "develop" ]

jobs:
  evaluar-calidad:
    runs-on: ubuntu-latest

    steps:
      - name: 1. Descargar repositorio
        uses: actions/checkout@v4

      - name: 2. Configurar Python 3.11
        uses: actions/setup-python@v5
        with:
          python-version: '3.11'

      - name: 3. Instalar dependencias
        run: |
          python -m pip install --upgrade pip
          pip install ruff pytest pytest-cov
          if [ -f requirements.txt ]; then pip install -r requirements.txt; fi

      - name: 4. Ejecutar Linter (Ruff)
        run: |
          mkdir -p target
          ruff check . --output-format=sarif > target/ruff-report.sarif || true

      - name: 5. Enviar reporte de Linter a UNQlassroom
        if: always()
        run: |
          REPORT_FILE="target/ruff-report.sarif"
          if [ ! -f "${'$'}REPORT_FILE" ] || [ ! -s "${'$'}REPORT_FILE" ]; then
            echo '{"version":"2.1.0","runs":[]}' > empty-sarif.json
            REPORT_FILE="empty-sarif.json"
          fi
          SARIF_CONTENT=${'$'}(jq -Rs . < "${'$'}REPORT_FILE")

          PAYLOAD=${'$'}(jq -n \
            --arg repo "${'$'}{{ github.event.repository.name }}" \
            --arg sha "${'$'}{{ github.event.pull_request.head.sha || github.sha }}" \
            --arg author "${'$'}{{ github.actor }}" \
            --arg run_id "${'$'}{{ github.run_id }}" \
            --arg tool "ruff" \
            --argjson sarif "${'$'}SARIF_CONTENT" \
            '{
              repository: { name: ${'$'}repo },
              sha: ${'$'}sha,
              head_commit: { id: ${'$'}sha, author: { username: ${'$'}author } },
              run_id: (${'$'}run_id | tonumber),
              herramienta: ${'$'}tool,
              sarif: (${'$'}sarif | fromjson)
            }')

          curl -X POST "$targetUrl" \
            -H "Content-Type: application/json" \
            -H "X-GitHub-Event: linter_report" \
            -d "${'$'}PAYLOAD"

      - name: 6. Ejecutar Tests y Cobertura (Pytest)
        run: |
          mkdir -p target/surefire-reports
          pytest --junitxml=target/surefire-reports/TEST-pytest.xml --cov=. --cov-report=xml:target/coverage.xml || true

      - name: 7. Enviar reporte de Cobertura, Tests y Líneas de Commit a UNQlassroom
        if: always()
        run: |
          # 1. Leer reportes de JUnit si existen
          SUREFIRE_XML=""
          if compgen -G "target/surefire-reports/TEST-*.xml" > /dev/null; then
            SUREFIRE_XML="<testsuites>${'$'}(cat target/surefire-reports/TEST-*.xml)</testsuites>"
          fi
          JUNIT_XML=${'$'}(echo "${'$'}SUREFIRE_XML" | jq -Rs .)

          # 2. Extraer cobertura si existe
          COV_PERCENT="0"
          if [ -f "target/coverage.xml" ]; then
            COV_PERCENT=${'$'}(python3 -c "import xml.etree.ElementTree as ET; tree=ET.parse('target/coverage.xml'); root=tree.getroot(); print(round(float(root.attrib.get('line-rate', 0))*100, 2))" 2>/dev/null || echo "0")
          fi

          # 3. Calcular líneas agregadas y eliminadas del commit con Git
          PARENT=${'$'}(git rev-parse --verify HEAD~1 2>/dev/null || echo "")
          if [ -n "${'$'}PARENT" ]; then
            INSERTIONS=${'$'}(git diff --numstat HEAD~1 HEAD | awk '{s+=${'$'}1} END {print s+0}')
            DELETIONS=${'$'}(git diff --numstat HEAD~1 HEAD | awk '{s+=${'$'}2} END {print s+0}')
            FILES_CHANGED=${'$'}(git diff --numstat HEAD~1 HEAD | wc -l | tr -d ' ')
          else
            INSERTIONS=${'$'}(git diff --numstat 4b825dc642cb6eb9a060e54bf8d69288fbee4904 HEAD 2>/dev/null | awk '{s+=${'$'}1} END {print s+0}')
            DELETIONS=0
            FILES_CHANGED=${'$'}(git diff --numstat 4b825dc642cb6eb9a060e54bf8d69288fbee4904 HEAD 2>/dev/null | wc -l | tr -d ' ')
          fi
          TOTAL_LINES=${'$'}((INSERTIONS + DELETIONS))
          COMMIT_MSG=${'$'}(git log -1 --pretty=%B)

          # 4. Construir y enviar payload
          PAYLOAD_CI=${'$'}(jq -n \
            --arg repo "${'$'}{{ github.event.repository.name }}" \
            --arg sha "${'$'}{{ github.event.pull_request.head.sha || github.sha }}" \
            --arg author "${'$'}{{ github.actor }}" \
            --arg run_id "${'$'}{{ github.run_id }}" \
            --arg msg "${'$'}COMMIT_MSG" \
            --arg insertions "${'$'}INSERTIONS" \
            --arg deletions "${'$'}DELETIONS" \
            --arg total_lines "${'$'}TOTAL_LINES" \
            --arg files_changed "${'$'}FILES_CHANGED" \
            --arg cov_percent "${'$'}COV_PERCENT" \
            --argjson junit "${'$'}JUNIT_XML" \
            '{
              repository: { name: ${'$'}repo },
              sha: ${'$'}sha,
              head_commit: {
                id: ${'$'}sha,
                message: ${'$'}msg,
                author: { username: ${'$'}author },
                stats: {
                  additions: (${'$'}insertions | tonumber),
                  deletions: (${'$'}deletions | tonumber),
                  total: (${'$'}total_lines | tonumber),
                  files_changed: (${'$'}files_changed | tonumber)
                }
              },
              run_id: (${'$'}run_id | tonumber),
              cobertura_lineas: (${'$'}cov_percent | tonumber),
              junit_xml: ${'$'}junit
            }')

          curl -X POST "$targetUrl" \
            -H "Content-Type: application/json" \
            -H "X-GitHub-Event: coverage_report" \
            -d "${'$'}PAYLOAD_CI"
""".trimIndent() + "\n"
    }

    private fun construirWorkflowCINode(): String {
        val targetUrl = targetWebhookUrl
        return """
name: CI Linter & Tests (Node.js)

on:
  push:
    branches: [ "main", "master", "develop" ]
  pull_request:
    branches: [ "main", "master", "develop" ]

jobs:
  evaluar-calidad:
    runs-on: ubuntu-latest

    steps:
      - name: 1. Descargar repositorio
        uses: actions/checkout@v4

      - name: 2. Configurar Node.js 20
        uses: actions/setup-node@v4
        with:
          node-version: '20'

      - name: 3. Instalar dependencias
        run: |
          if [ -f package-lock.json ]; then
            npm ci || npm install || true
          else
            npm install || true
          fi

      - name: 4. Ejecutar Linter (ESLint)
        run: |
          mkdir -p target
          npx eslint . -f checkstyle -o target/checkstyle-result.xml || true

      - name: 5. Enviar reporte de Linter a UNQlassroom
        if: always()
        run: |
          REPORT_FILE="target/checkstyle-result.xml"
          if [ ! -f "${'$'}REPORT_FILE" ]; then
            echo '<?xml version="1.0" encoding="UTF-8"?><checkstyle version="10.0"></checkstyle>' > empty-checkstyle.xml
            REPORT_FILE="empty-checkstyle.xml"
          fi
          XML_CONTENT=${'$'}(jq -Rs . < "${'$'}REPORT_FILE")

          PAYLOAD=${'$'}(jq -n \
            --arg repo "${'$'}{{ github.event.repository.name }}" \
            --arg sha "${'$'}{{ github.event.pull_request.head.sha || github.sha }}" \
            --arg author "${'$'}{{ github.actor }}" \
            --arg run_id "${'$'}{{ github.run_id }}" \
            --arg tool "eslint" \
            --argjson xml "${'$'}XML_CONTENT" \
            '{
              repository: { name: ${'$'}repo },
              sha: ${'$'}sha,
              head_commit: { id: ${'$'}sha, author: { username: ${'$'}author } },
              run_id: (${'$'}run_id | tonumber),
              herramienta: ${'$'}tool,
              checkstyle_xml: ${'$'}xml
            }')

          curl -X POST "$targetUrl" \
            -H "Content-Type: application/json" \
            -H "X-GitHub-Event: linter_report" \
            -d "${'$'}PAYLOAD"

      - name: 6. Ejecutar Tests y Cobertura
        run: |
          mkdir -p target/surefire-reports
          if npm run | grep -q "test"; then
            npm test -- --coverage --ci --testResultsProcessor=jest-junit || npm test || true
          fi
          if [ -f junit.xml ]; then
            mv junit.xml target/surefire-reports/TEST-jest.xml
          fi

      - name: 7. Enviar reporte de Cobertura, Tests y Líneas de Commit a UNQlassroom
        if: always()
        run: |
          # 1. Leer reportes de JUnit si existen
          SUREFIRE_XML=""
          if compgen -G "target/surefire-reports/TEST-*.xml" > /dev/null; then
            SUREFIRE_XML="<testsuites>${'$'}(cat target/surefire-reports/TEST-*.xml)</testsuites>"
          fi
          JUNIT_XML=${'$'}(echo "${'$'}SUREFIRE_XML" | jq -Rs .)

          # 2. Extraer cobertura de coverage/coverage-summary.json si existe
          COV_PERCENT="0"
          if [ -f "coverage/coverage-summary.json" ]; then
            COV_PERCENT=${'$'}(jq -r '.total.lines.pct // 0' coverage/coverage-summary.json 2>/dev/null || echo "0")
          fi

          # 3. Calcular líneas agregadas y eliminadas del commit con Git
          PARENT=${'$'}(git rev-parse --verify HEAD~1 2>/dev/null || echo "")
          if [ -n "${'$'}PARENT" ]; then
            INSERTIONS=${'$'}(git diff --numstat HEAD~1 HEAD | awk '{s+=${'$'}1} END {print s+0}')
            DELETIONS=${'$'}(git diff --numstat HEAD~1 HEAD | awk '{s+=${'$'}2} END {print s+0}')
            FILES_CHANGED=${'$'}(git diff --numstat HEAD~1 HEAD | wc -l | tr -d ' ')
          else
            INSERTIONS=${'$'}(git diff --numstat 4b825dc642cb6eb9a060e54bf8d69288fbee4904 HEAD 2>/dev/null | awk '{s+=${'$'}1} END {print s+0}')
            DELETIONS=0
            FILES_CHANGED=${'$'}(git diff --numstat 4b825dc642cb6eb9a060e54bf8d69288fbee4904 HEAD 2>/dev/null | wc -l | tr -d ' ')
          fi
          TOTAL_LINES=${'$'}((INSERTIONS + DELETIONS))
          COMMIT_MSG=${'$'}(git log -1 --pretty=%B)

          # 4. Construir y enviar payload
          PAYLOAD_CI=${'$'}(jq -n \
            --arg repo "${'$'}{{ github.event.repository.name }}" \
            --arg sha "${'$'}{{ github.event.pull_request.head.sha || github.sha }}" \
            --arg author "${'$'}{{ github.actor }}" \
            --arg run_id "${'$'}{{ github.run_id }}" \
            --arg msg "${'$'}COMMIT_MSG" \
            --arg insertions "${'$'}INSERTIONS" \
            --arg deletions "${'$'}DELETIONS" \
            --arg total_lines "${'$'}TOTAL_LINES" \
            --arg files_changed "${'$'}FILES_CHANGED" \
            --arg cov_percent "${'$'}COV_PERCENT" \
            --argjson junit "${'$'}JUNIT_XML" \
            '{
              repository: { name: ${'$'}repo },
              sha: ${'$'}sha,
              head_commit: {
                id: ${'$'}sha,
                message: ${'$'}msg,
                author: { username: ${'$'}author },
                stats: {
                  additions: (${'$'}insertions | tonumber),
                  deletions: (${'$'}deletions | tonumber),
                  total: (${'$'}total_lines | tonumber),
                  files_changed: (${'$'}files_changed | tonumber)
                }
              },
              run_id: (${'$'}run_id | tonumber),
              cobertura_lineas: (${'$'}cov_percent | tonumber),
              junit_xml: ${'$'}junit
            }')

          curl -X POST "$targetUrl" \
            -H "Content-Type: application/json" \
            -H "X-GitHub-Event: coverage_report" \
            -d "${'$'}PAYLOAD_CI"
""".trimIndent() + "\n"
    }

    private fun construirWorkflowCIDotnet(): String {
        val targetUrl = targetWebhookUrl
        return """
name: CI Linter & Tests (.NET)

on:
  push:
    branches: [ "main", "master", "develop" ]
  pull_request:
    branches: [ "main", "master", "develop" ]

jobs:
  evaluar-calidad:
    runs-on: ubuntu-latest

    steps:
      - name: 1. Descargar repositorio
        uses: actions/checkout@v4

      - name: 2. Configurar .NET 8
        uses: actions/setup-dotnet@v4
        with:
          dotnet-version: '8.0.x'

      - name: 3. Restaurar dependencias
        run: dotnet restore || true

      - name: 4. Ejecutar Linter / Formato
        run: |
          mkdir -p target
          dotnet format --verify-no-changes --report ./target/format-report.json || true

      - name: 5. Ejecutar Tests y Cobertura (Coverlet)
        run: |
          mkdir -p target/surefire-reports
          dotnet test --logger "junit;LogFilePath=target/surefire-reports/TEST-dotnet.xml" /p:CollectCoverage=true /p:CoverletOutputFormat=cobertura /p:CoverletOutput=./target/coverage.cobertura.xml || true

      - name: 6. Enviar reporte de Cobertura, Tests y Líneas de Commit a UNQlassroom
        if: always()
        run: |
          # 1. Leer reportes de JUnit si existen
          SUREFIRE_XML=""
          if compgen -G "target/surefire-reports/TEST-*.xml" > /dev/null; then
            SUREFIRE_XML="<testsuites>${'$'}(cat target/surefire-reports/TEST-*.xml)</testsuites>"
          fi
          JUNIT_XML=${'$'}(echo "${'$'}SUREFIRE_XML" | jq -Rs .)

          # 2. Extraer cobertura si existe
          COV_PERCENT="0"
          if [ -f "target/coverage.cobertura.xml" ]; then
            COV_PERCENT=${'$'}(python3 -c "import xml.etree.ElementTree as ET; tree=ET.parse('target/coverage.cobertura.xml'); root=tree.getroot(); print(round(float(root.attrib.get('line-rate', 0))*100, 2))" 2>/dev/null || echo "0")
          fi

          # 3. Calcular líneas agregadas y eliminadas del commit con Git
          PARENT=${'$'}(git rev-parse --verify HEAD~1 2>/dev/null || echo "")
          if [ -n "${'$'}PARENT" ]; then
            INSERTIONS=${'$'}(git diff --numstat HEAD~1 HEAD | awk '{s+=${'$'}1} END {print s+0}')
            DELETIONS=${'$'}(git diff --numstat HEAD~1 HEAD | awk '{s+=${'$'}2} END {print s+0}')
            FILES_CHANGED=${'$'}(git diff --numstat HEAD~1 HEAD | wc -l | tr -d ' ')
          else
            INSERTIONS=${'$'}(git diff --numstat 4b825dc642cb6eb9a060e54bf8d69288fbee4904 HEAD 2>/dev/null | awk '{s+=${'$'}1} END {print s+0}')
            DELETIONS=0
            FILES_CHANGED=${'$'}(git diff --numstat 4b825dc642cb6eb9a060e54bf8d69288fbee4904 HEAD 2>/dev/null | wc -l | tr -d ' ')
          fi
          TOTAL_LINES=${'$'}((INSERTIONS + DELETIONS))
          COMMIT_MSG=${'$'}(git log -1 --pretty=%B)

          # 4. Construir y enviar payload
          PAYLOAD_CI=${'$'}(jq -n \
            --arg repo "${'$'}{{ github.event.repository.name }}" \
            --arg sha "${'$'}{{ github.event.pull_request.head.sha || github.sha }}" \
            --arg author "${'$'}{{ github.actor }}" \
            --arg run_id "${'$'}{{ github.run_id }}" \
            --arg msg "${'$'}COMMIT_MSG" \
            --arg insertions "${'$'}INSERTIONS" \
            --arg deletions "${'$'}DELETIONS" \
            --arg total_lines "${'$'}TOTAL_LINES" \
            --arg files_changed "${'$'}FILES_CHANGED" \
            --arg cov_percent "${'$'}COV_PERCENT" \
            --argjson junit "${'$'}JUNIT_XML" \
            '{
              repository: { name: ${'$'}repo },
              sha: ${'$'}sha,
              head_commit: {
                id: ${'$'}sha,
                message: ${'$'}msg,
                author: { username: ${'$'}author },
                stats: {
                  additions: (${'$'}insertions | tonumber),
                  deletions: (${'$'}deletions | tonumber),
                  total: (${'$'}total_lines | tonumber),
                  files_changed: (${'$'}files_changed | tonumber)
                }
              },
              run_id: (${'$'}run_id | tonumber),
              cobertura_lineas: (${'$'}cov_percent | tonumber),
              junit_xml: ${'$'}junit
            }')

          curl -X POST "$targetUrl" \
            -H "Content-Type: application/json" \
            -H "X-GitHub-Event: coverage_report" \
            -d "${'$'}PAYLOAD_CI"
""".trimIndent() + "\n"
    }
    companion object {
        private val JACOCO_MAVEN_PLUGIN_SNIPPET = """
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.15</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>prepare-agent</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>test</phase>
                        <goals>
                            <goal>report</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>""".trimMargin("___")

        private val CHECKSTYLE_MAVEN_PLUGIN_SNIPPET = """
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-checkstyle-plugin</artifactId>
                <version>3.3.1</version>
            </plugin>""".trimMargin("___")

        private val SUREFIRE_MAVEN_PLUGIN_SNIPPET = """
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>""".trimMargin("___")

        val DEFAULT_POM_XML = """
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>ar.edu.unq</groupId>
    <artifactId>tp-template</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>0.8.15</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>prepare-agent</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>test</phase>
                        <goals>
                            <goal>report</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-checkstyle-plugin</artifactId>
                <version>3.3.1</version>
            </plugin>
        </plugins>
    </build>
</project>
""".trimIndent() + "\n"

        val RESCUE_SANITY_JAVA = """
/**
 * Clase de sanidad por defecto para validacion de calidad.
 */
public class Sanity {

  public boolean check() {
    return true;
  }
}
""".trimIndent() + "\n"

        val RESCUE_SANITY_TEST_JAVA = """
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Test de sanidad por defecto.
 */
class SanityTest {

  @Test
  void sanityTest() {
    Sanity sanity = new Sanity();
    assertTrue(sanity.check());
  }
}
""".trimIndent() + "\n"

        val RESCUE_SANITY_PYTHON = """
def test_sanity():
    \"\"\"Test de sanidad por defecto para validacion en CI de UNQlassroom.\"\"\"
    assert True
""".trimIndent() + "\n"

        val RESCUE_SANITY_NODE = """
// Test de sanidad por defecto para validacion en CI de UNQlassroom
describe('Sanity test', () => {
  it('debe pasar la verificacion inicial', () => {
    expect(true).toBe(true);
  });
});
""".trimIndent() + "\n"

        val MAVEN_WRAPPER_PROPERTIES = """
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
""".trimIndent() + "\n"

        val MVNW_UNIX_SCRIPT = """
#!/bin/sh
# Apache Maven Wrapper startup script (only-script mode)
set -euf
if command -v mvn >/dev/null 2>&1; then
  exec mvn "${'$'}@"
fi
scriptDir="${'$'}(dirname "${'$'}0")"
distributionUrl="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip"
if [ -f "${'$'}scriptDir/.mvn/wrapper/maven-wrapper.properties" ]; then
  while IFS="=" read -r key value; do
    case "${'$'}{key-}" in
      distributionUrl) distributionUrl=${'$'}(printf "%s" "${'$'}{value-}" | tr -d '[:space:]') ;;
    esac
  done <"${'$'}scriptDir/.mvn/wrapper/maven-wrapper.properties"
fi
MAVEN_HOME="${'$'}{HOME}/.m2/wrapper/dists/apache-maven-bin"
if [ ! -x "${'$'}MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "${'$'}MAVEN_HOME"
  tmpZip="${'$'}(mktemp)"
  curl -fsSL "${'$'}distributionUrl" -o "${'$'}tmpZip"
  unzip -q "${'$'}tmpZip" -d "${'$'}MAVEN_HOME"
  rm -f "${'$'}tmpZip"
  extractedDir=${'$'}(find "${'$'}MAVEN_HOME" -maxdepth 1 -type d -name "apache-maven-*" | head -n 1)
  if [ -n "${'$'}extractedDir" ]; then
    cp -r "${'$'}extractedDir"/* "${'$'}MAVEN_HOME"/
  fi
fi
exec "${'$'}MAVEN_HOME/bin/mvn" "${'$'}@"
""".trimIndent() + "\n"

        val MVNW_WINDOWS_SCRIPT = """
@ECHO OFF
SETLOCAL
WHERE mvn >NUL 2>NUL
IF %ERRORLEVEL% EQU 0 (
  mvn %*
  EXIT /B %ERRORLEVEL%
)
ECHO Maven wrapper requires mvn in PATH or PowerShell downloader. >&2
EXIT /B 1
""".trimIndent() + "\n"

        val GRADLE_WRAPPER_PROPERTIES = """
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""".trimIndent() + "\n"

        val GRADLEW_UNIX_SCRIPT = """
#!/bin/sh
# Gradle Wrapper startup script
if command -v gradle >/dev/null 2>&1; then
  exec gradle "${'$'}@"
fi
echo "Gradle wrapper script executed" >&2
exit 1
""".trimIndent() + "\n"

        val GRADLEW_WINDOWS_SCRIPT = """
@ECHO OFF
WHERE gradle >NUL 2>NUL
IF %ERRORLEVEL% EQU 0 (
  gradle %*
  EXIT /B %ERRORLEVEL%
)
ECHO Gradle wrapper requires gradle in PATH. >&2
EXIT /B 1
""".trimIndent() + "\n"
    }
}

