package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.integration.github.config.GitHubAppProperties
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
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

    @Transactional
    override fun asegurarTemplateEstandarizado(templateRepoName: String): Repositorio {
        val cleanName = templateRepoName.trim()
        require(cleanName.isNotEmpty()) { "El nombre del repositorio template no puede estar vacío" }

        val org = gitHubAppProperties.organization?.trim()?.takeIf { it.isNotEmpty() } ?: "UNQlassroom"
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

        // 1. Inspección y parcheo quirúrgico del gestor de dependencias (pom.xml / build.gradle / build.gradle.kts)
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

        // 2. Asegurar Wrapper (Maven Wrapper o Gradle Wrapper, cualquiera de los dos)
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

        // 3. Estructura mínima: clase base en src/main/java y test de rescate en src/test/java
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

        // 4. Inyección forzosa del workflow de CI (.github/workflows/ci.yml)
        val ciWorkflowPath = ".github/workflows/ci.yml"
        val existingCiSha = archivosEnArbol[ciWorkflowPath]
            ?: if (!tieneArbol) gitHubRepoService.getFileContent(cleanName, ciWorkflowPath)?.sha else null

        logger.info("Inyectando/pisando obligatoriamente {} en el template '{}'", ciWorkflowPath, cleanName)
        escribirArchivo(
            path = ciWorkflowPath,
            content = construirWorkflowCI(),
            message = "ci: inyectar workflow estandarizado de calidad UNQlassroom",
            knownSha = existingCiSha,
        )

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

    override fun construirWorkflowCI(): String {
        val targetUrl = webhookPublicUrl.trim().ifBlank { "https://smee.io/bP2Po2TmL7DMmaGd" }
        return """
name: CI Linter & Tests

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
          cache: 'maven'

      - name: 3. Ejecutar Linter (Checkstyle)
        run: |
          chmod +x mvnw || true
          ./mvnw org.apache.maven.plugins:maven-checkstyle-plugin:3.3.1:checkstyle \
            -Dcheckstyle.config.location=google_checks.xml \
            -Dcheckstyle.violationSeverity=warning || true

      - name: 4. Enviar reporte de Linter a UNQlassroom
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
          # Corre todos los tests generando reportes de Surefire y el XML de JaCoCo aun si algún test falla
          ./mvnw test jacoco:report -Dmaven.test.failure.ignore=true || true

      - name: 6. Enviar reporte de Cobertura, Tests y Líneas de Commit a UNQlassroom
        if: always()
        run: |
          # 1. Leer JaCoCo si existe
          JACOCO_FILE="target/site/jacoco/jacoco.xml"
          if [ -f "${'$'}JACOCO_FILE" ]; then
            JACOCO_XML=${'$'}(jq -Rs . < "${'$'}JACOCO_FILE")
          else
            JACOCO_XML='""'
          fi

          # 2. Leer reportes de JUnit Surefire si existen
          SUREFIRE_XML=""
          if compgen -G "target/surefire-reports/TEST-*.xml" > /dev/null; then
            SUREFIRE_XML="<testsuites>${'$'}(cat target/surefire-reports/TEST-*.xml)</testsuites>"
          fi
          JUNIT_XML=${'$'}(echo "${'$'}SUREFIRE_XML" | jq -Rs .)

          # 3. Calcular líneas agregadas y eliminadas del commit con Git
          PARENT=${'$'}(git rev-parse --verify HEAD~1 2>/dev/null || echo "")
          if [ -n "${'$'}PARENT" ]; then
            INSERTIONS=${'$'}(git diff --numstat HEAD~1 HEAD | awk '{s+=${'$'}1} END {print s+0}')
            DELETIONS=${'$'}(git diff --numstat HEAD~1 HEAD | awk '{s+=${'$'}2} END {print s+0}')
            FILES_CHANGED=${'$'}(git diff --numstat HEAD~1 HEAD | wc -l | tr -d ' ')
          else
            # Si es el primer commit del repositorio
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
public class Sanity {

    public boolean check() {
        return true;
    }
}
""".trimIndent() + "\n"

        val RESCUE_SANITY_TEST_JAVA = """
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SanityTest {

    @Test
    void sanityTest() {
        Sanity sanity = new Sanity();
        assertTrue(sanity.check());
    }
}
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

