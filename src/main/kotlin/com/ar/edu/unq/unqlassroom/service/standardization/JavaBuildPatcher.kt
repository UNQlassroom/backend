package com.ar.edu.unq.unqlassroom.service.standardization

import org.springframework.stereotype.Component

@Component
class JavaBuildPatcher {

    fun analizarYParchearPom(pomContent: String): String? {
        if (pomContent.isBlank()) {
            return StandardizationResourceLoader.load("java/pom.xml")
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

    fun analizarYParchearGradle(gradleContent: String, esKotlinDsl: Boolean): String? {
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
    }
}
