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
    @Value("\${github.webhook.public-url:https://smee.io/bP2Po2TmL7DMmaGd}")
    private val webhookPublicUrl: String = "https://smee.io/bP2Po2TmL7DMmaGd",
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

        // 3. Estructura mínima y test de rescate en src/test/java
        val tieneTestsEnArbol = if (tieneArbol) {
            archivosEnArbol.keys.any { path ->
                path.startsWith("src/test/") && (path.endsWith(".java") || path.endsWith(".kt"))
            }
        } else {
            existeArchivo("src/test/java/SanityTest.java")
        }

        if (!tieneTestsEnArbol) {
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
        val targetUrl = webhookPublicUrl.trim()
        return """
name: UNQlassroom CI

on:
  push:
    branches: [ "main", "master" ]
  pull_request:
    branches: [ "main", "master" ]

jobs:
  build-and-analyze:
    runs-on: ubuntu-latest
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
      with:
        fetch-depth: 0

    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: maven

    # ==========================================
    # ETAPA 1: LINTER (Checkstyle)
    # ==========================================
    - name: Run Linter (Checkstyle)
      run: |
        chmod +x ./mvnw 2>/dev/null || true
        ./mvnw org.apache.maven.plugins:maven-checkstyle-plugin:3.3.1:checkstyle \
          -Dcheckstyle.config.location=google_checks.xml \
          -Dcheckstyle.violationSeverity=warning || true

    - name: Send Linter Report to Backend
      if: always()
      env:
        COMMIT_SHA: ${'$'}{{ github.sha }}
        REPO_NAME: ${'$'}{{ github.event.repository.name }}
      run: |
        CHECKSTYLE_PATH="target/checkstyle-result.xml"
        
        if [ -f "${'$'}CHECKSTYLE_PATH" ]; then
          echo "Reporte de Linter encontrado. Preparando envío..."
          
          jq -n \
            --arg repo "${'$'}REPO_NAME" \
            --arg sha "${'$'}COMMIT_SHA" \
            --rawfile xml "${'$'}CHECKSTYLE_PATH" \
            '{repo_name: ${'$'}repo, commit_sha: ${'$'}sha, checkstyle_xml: ${'$'}xml}' > linter_payload.json
            
          curl -X POST "$targetUrl" \
            -H "Content-Type: application/json" \
            -H "X-GitHub-Event: linter_report" \
            -d @linter_payload.json
        else
          echo "No se generó reporte de Checkstyle."
        fi

    # ==========================================
    # ETAPA 2: TESTS Y COBERTURA (JaCoCo) + TAMAÑO DE COMMIT
    # ==========================================
    - name: Run Tests and JaCoCo
      run: |
        chmod +x ./mvnw 2>/dev/null || true
        ./mvnw test jacoco:report -Dmaven.test.failure.ignore=true || true

    - name: Send Coverage and Test Report to Backend
      if: always()
      env:
        COMMIT_SHA: ${'$'}{{ github.sha }}
        REPO_NAME: ${'$'}{{ github.event.repository.name }}
      run: |
        JACOCO_PATH="target/site/jacoco/jacoco.xml"
        SUREFIRE_DIR="target/surefire-reports"
        
        if [ -d "${'$'}SUREFIRE_DIR" ] && ls ${'$'}SUREFIRE_DIR/TEST-*.xml 1> /dev/null 2>&1; then
          cat ${'$'}SUREFIRE_DIR/TEST-*.xml > combined_junit.xml
        else
          echo "" > combined_junit.xml
        fi
        
        if [ ! -f "${'$'}JACOCO_PATH" ]; then
          echo "" > empty_jacoco.xml
          JACOCO_PATH="empty_jacoco.xml"
        fi

        # --- NUEVA LÓGICA: EXTRAER MÉTRICAS DE GIT ---
        COMMIT_MSG=${'$'}(git log -1 --pretty=%B)
        
        # Verificamos si existe un commit padre (por si es el Initial Commit)
        if git rev-parse --verify HEAD~1 >/dev/null 2>&1; then
          STATS=${'$'}(git diff --numstat HEAD~1 HEAD | awk '{ins+=${'$'}1; del+=${'$'}2; files+=1} END {print ins+0, del+0, files+0}')
          read INSERTIONS DELETIONS FILES_CHANGED <<< "${'$'}STATS"
        else
          INSERTIONS=0
          DELETIONS=0
          FILES_CHANGED=0
        fi
        
        TOTAL_LINES=${'$'}((INSERTIONS + DELETIONS))
        echo "Métricas de Git calculadas: Archivos=${'$'}FILES_CHANGED, Líneas=${'$'}TOTAL_LINES (+${'$'}INSERTIONS -${'$'}DELETIONS)"
        # ---------------------------------------------

        if [ -s "${'$'}JACOCO_PATH" ] || [ -s "combined_junit.xml" ]; then
          echo "Empaquetando reportes de Tests, Cobertura y Git..."
          
          # Usamos --argjson para pasar los números como enteros reales y no como strings
          jq -n \
            --arg repo "${'$'}REPO_NAME" \
            --arg sha "${'$'}COMMIT_SHA" \
            --arg msg "${'$'}COMMIT_MSG" \
            --argjson files "${'$'}FILES_CHANGED" \
            --argjson lines "${'$'}TOTAL_LINES" \
            --argjson ins "${'$'}INSERTIONS" \
            --argjson del "${'$'}DELETIONS" \
            --rawfile jacoco "${'$'}JACOCO_PATH" \
            --rawfile junit "combined_junit.xml" \
            '{
              repo_name: ${'$'}repo, 
              commit_sha: ${'$'}sha, 
              commit_message: ${'$'}msg,
              files_changed: ${'$'}files,
              total_lines_modified: ${'$'}lines,
              insertions: ${'$'}ins,
              deletions: ${'$'}del,
              jacoco_xml: ${'$'}jacoco, 
              junit_xml: ${'$'}junit
            }' > coverage_payload.json
            
          curl -X POST "$targetUrl" \
            -H "Content-Type: application/json" \
            -H "X-GitHub-Event: coverage_report" \
            -d @coverage_payload.json
        else
          echo "No se encontraron reportes de pruebas ni de cobertura para enviar."
        fi
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

        val RESCUE_SANITY_TEST_JAVA = """
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SanityTest {

    @Test
    void sanityTest() {
        assertTrue(true);
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

