package com.ar.edu.unq.unqlassroom.service.standardization

import com.ar.edu.unq.unqlassroom.model.LenguajeProyecto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class JavaTemplateHandler(
    private val javaBuildPatcher: JavaBuildPatcher,
    private val workflowCiBuilder: WorkflowCiBuilder,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun estandarizar(ctx: TemplateRepoContext, lenguaje: LenguajeProyecto) {
        val pomFile = ctx.obtenerArchivo("pom.xml")
        val gradleKtsFile = if (pomFile == null) ctx.obtenerArchivo("build.gradle.kts") else null
        val gradleGroovyFile = if (pomFile == null && gradleKtsFile == null) ctx.obtenerArchivo("build.gradle") else null

        val esProyectoGradle = gradleKtsFile != null || gradleGroovyFile != null || lenguaje == LenguajeProyecto.JAVA_GRADLE

        when {
            pomFile != null -> {
                val pomContent = pomFile.decodedContent()
                val pomParcheado = javaBuildPatcher.analizarYParchearPom(pomContent)
                if (pomParcheado != null) {
                    logger.info("Parcheando pom.xml en template '{}' para asegurar JaCoCo y Checkstyle", ctx.repoName)
                    ctx.escribirArchivo(
                        path = "pom.xml",
                        content = pomParcheado,
                        message = "chore(ci): asegurar dependencias y plugins de JaCoCo y Checkstyle en pom.xml",
                        knownSha = pomFile.sha,
                    )
                }
            }
            gradleKtsFile != null -> {
                val gradleContent = gradleKtsFile.decodedContent()
                val gradleParcheado = javaBuildPatcher.analizarYParchearGradle(gradleContent, esKotlinDsl = true)
                if (gradleParcheado != null) {
                    logger.info("Parcheando build.gradle.kts en template '{}' para asegurar JaCoCo y Checkstyle", ctx.repoName)
                    ctx.escribirArchivo(
                        path = "build.gradle.kts",
                        content = gradleParcheado,
                        message = "chore(ci): asegurar configuración de JaCoCo y Checkstyle en build.gradle.kts",
                        knownSha = gradleKtsFile.sha,
                    )
                }
            }
            gradleGroovyFile != null -> {
                val gradleContent = gradleGroovyFile.decodedContent()
                val gradleParcheado = javaBuildPatcher.analizarYParchearGradle(gradleContent, esKotlinDsl = false)
                if (gradleParcheado != null) {
                    logger.info("Parcheando build.gradle en template '{}' para asegurar JaCoCo y Checkstyle", ctx.repoName)
                    ctx.escribirArchivo(
                        path = "build.gradle",
                        content = gradleParcheado,
                        message = "chore(ci): asegurar configuración de JaCoCo y Checkstyle en build.gradle",
                        knownSha = gradleGroovyFile.sha,
                    )
                }
            }
            else -> {
                logger.info("El template '{}' no posee pom.xml ni build.gradle; inyectando pom.xml base", ctx.repoName)
                ctx.escribirArchivo(
                    path = "pom.xml",
                    content = StandardizationResourceLoader.load("java/pom.xml"),
                    message = "chore(ci): agregar pom.xml base con JaCoCo, Checkstyle y JUnit 5",
                )
            }
        }

        asegurarWrappers(ctx, esProyectoGradle)
        asegurarClaseYTestDeSanidad(ctx)
        inyectarWorkflowCI(ctx, esProyectoGradle)
    }

    private fun asegurarWrappers(ctx: TemplateRepoContext, esProyectoGradle: Boolean) {
        val tieneMavenWrapperCompleto = ctx.existeArchivo("mvnw") &&
            ctx.existeArchivo("mvnw.cmd") &&
            ctx.existeArchivo(".mvn/wrapper/maven-wrapper.properties")

        val tieneGradleWrapperCompleto = ctx.existeArchivo("gradlew") &&
            ctx.existeArchivo("gradlew.bat") &&
            ctx.existeArchivo("gradle/wrapper/gradle-wrapper.properties")

        if (!tieneMavenWrapperCompleto && !tieneGradleWrapperCompleto) {
            if (esProyectoGradle) {
                if (!ctx.existeArchivo("gradle/wrapper/gradle-wrapper.properties")) {
                    ctx.escribirArchivo(
                        path = "gradle/wrapper/gradle-wrapper.properties",
                        content = StandardizationResourceLoader.load("gradle/gradle-wrapper.properties"),
                        message = "chore(ci): agregar gradle-wrapper.properties",
                    )
                }
                if (!ctx.existeArchivo("gradlew")) {
                    ctx.escribirArchivo(
                        path = "gradlew",
                        content = StandardizationResourceLoader.load("gradle/gradlew"),
                        message = "chore(ci): agregar script gradlew",
                    )
                }
                if (!ctx.existeArchivo("gradlew.bat")) {
                    ctx.escribirArchivo(
                        path = "gradlew.bat",
                        content = StandardizationResourceLoader.load("gradle/gradlew.bat"),
                        message = "chore(ci): agregar script gradlew.bat",
                    )
                }
            } else {
                if (!ctx.existeArchivo(".mvn/wrapper/maven-wrapper.properties")) {
                    ctx.escribirArchivo(
                        path = ".mvn/wrapper/maven-wrapper.properties",
                        content = StandardizationResourceLoader.load("java/maven-wrapper.properties"),
                        message = "chore(ci): agregar maven-wrapper.properties",
                    )
                }
                if (!ctx.existeArchivo("mvnw")) {
                    ctx.escribirArchivo(
                        path = "mvnw",
                        content = StandardizationResourceLoader.load("java/mvnw"),
                        message = "chore(ci): agregar script mvnw",
                    )
                }
                if (!ctx.existeArchivo("mvnw.cmd")) {
                    ctx.escribirArchivo(
                        path = "mvnw.cmd",
                        content = StandardizationResourceLoader.load("java/mvnw.cmd"),
                        message = "chore(ci): agregar script mvnw.cmd",
                    )
                }
            }
        }
    }

    private fun asegurarClaseYTestDeSanidad(ctx: TemplateRepoContext) {
        val tieneTestsEnArbol = if (ctx.tieneArbol) {
            ctx.archivosEnArbol.keys.any { path ->
                path.startsWith("src/test/") && (path.endsWith(".java") || path.endsWith(".kt"))
            }
        } else {
            ctx.existeArchivo("src/test/java/SanityTest.java")
        }

        if (!tieneTestsEnArbol) {
            if (!ctx.existeArchivo("src/main/java/Sanity.java")) {
                logger.info("El template '{}' no posee tests en src/test; inyectando clase de rescate Sanity.java", ctx.repoName)
                ctx.escribirArchivo(
                    path = "src/main/java/Sanity.java",
                    content = StandardizationResourceLoader.load("java/Sanity.java"),
                    message = "feat(ci): agregar clase de rescate por defecto en src/main/java/Sanity.java",
                )
            }

            logger.info("El template '{}' no posee tests en src/test; inyectando test de rescate SanityTest.java", ctx.repoName)
            ctx.escribirArchivo(
                path = "src/test/java/SanityTest.java",
                content = StandardizationResourceLoader.load("java/SanityTest.java"),
                message = "test(ci): agregar test de rescate por defecto en src/test/java/SanityTest.java",
            )
        }
    }

    private fun inyectarWorkflowCI(ctx: TemplateRepoContext, esGradle: Boolean) {
        val ciWorkflowPath = ".github/workflows/ci.yml"
        val existingCiSha = ctx.archivosEnArbol[ciWorkflowPath]
            ?: if (!ctx.tieneArbol) ctx.obtenerArchivo(ciWorkflowPath)?.sha else null

        logger.info("Inyectando/pisando obligatoriamente {} en el template '{}' (Java)", ciWorkflowPath, ctx.repoName)
        ctx.escribirArchivo(
            path = ciWorkflowPath,
            content = workflowCiBuilder.buildJava(esGradle),
            message = "ci: inyectar workflow estandarizado de calidad UNQlassroom para Java",
            knownSha = existingCiSha,
        )
    }
}
