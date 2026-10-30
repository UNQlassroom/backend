package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.exception.GitHubApiException
import com.ar.edu.unq.unqlassroom.integration.github.client.GitHubAppClient
import com.ar.edu.unq.unqlassroom.integration.github.config.GitHubAppProperties
import com.ar.edu.unq.unqlassroom.integration.github.service.*
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.ar.edu.unq.unqlassroom.service.impl.TemplateStandardizationServiceImpl
import com.ar.edu.unq.unqlassroom.service.webhook.PushWebhookHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatcher
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.charset.StandardCharsets
import java.util.Base64

@ExtendWith(MockitoExtension::class)
class TemplateStandardizationServiceImplTest {

    @Mock
    private lateinit var gitHubRepoService: GitHubRepoService

    @Mock
    private lateinit var repositorioRepository: RepositorioRepository

    private lateinit var gitHubAppProperties: GitHubAppProperties
    private lateinit var service: TemplateStandardizationServiceImpl

    @BeforeEach
    fun setUp() {
        gitHubAppProperties = GitHubAppProperties(
            appId = "123",
            installationId = "456",
            organization = "UNQlassroom",
        )
        service = TemplateStandardizationServiceImpl(
            gitHubRepoService = gitHubRepoService,
            repositorioRepository = repositorioRepository,
            gitHubAppProperties = gitHubAppProperties,
            webhookPublicUrl = "https://webhook.unqlassroom.edu.ar/events",
        )
    }

    private fun encodeBase64(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))

    private fun <T : Any> eqNonNull(value: T): T = eq(value) ?: value

    private fun matchString(predicate: (String) -> Boolean): String =
        argThat(ArgumentMatcher<String> { it != null && predicate(it) }) ?: ""

    private fun anyRepositorio(): Repositorio {
        any(Repositorio::class.java)
        return Repositorio(nombre = "dummy", htmlUrl = "https://dummy")
    }

    @Test
    fun `asegurarTemplateEstandarizado con Cache HIT omite escaneo y llamadas a Contents API`() {
        val repoTemplate = Repositorio(
            id = 1L,
            nombre = "template-objetos-1",
            htmlUrl = "https://github.com/UNQlassroom/template-objetos-1",
            esTemplate = true,
            ultimoCommitSha = "sha-aprobado-123",
            templateAprobado = true,
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("template-objetos-1")).thenReturn(repoTemplate)
        `when`(gitHubRepoService.getUltimoCommit("template-objetos-1")).thenReturn(
            GitHubCommitResponse(sha = "sha-aprobado-123")
        )

        val result = service.asegurarTemplateEstandarizado("template-objetos-1")

        assertTrue(result.templateAprobado)
        assertEquals("sha-aprobado-123", result.ultimoCommitSha)
        verify(gitHubRepoService, never()).getRepositoryTree(anyString(), anyString(), anyBoolean(), any())
        verify(gitHubRepoService, never()).getFileContent(anyString(), anyString(), any())
        verify(gitHubRepoService, never()).createOrUpdateFile(anyString(), anyString(), anyString(), anyString(), any(), any(), any())
    }

    @Test
    fun `asegurarTemplateEstandarizado con Cache MISS parchea pom, inyecta wrapper, SanityTest y ci yml, y aprueba template`() {
        val repoTemplate = Repositorio(
            id = 2L,
            nombre = "template-nuevo",
            htmlUrl = "https://github.com/UNQlassroom/template-nuevo",
            esTemplate = true,
            ultimoCommitSha = "sha-viejo-000",
            templateAprobado = false,
        )

        val pomSinCalidad = """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>ar.edu.unq</groupId>
                <artifactId>ejemplo</artifactId>
                <version>1.0-SNAPSHOT</version>
            </project>
        """.trimIndent()

        `when`(repositorioRepository.findByNombreIgnoreCase("template-nuevo")).thenReturn(repoTemplate)
        `when`(gitHubRepoService.getUltimoCommit("template-nuevo")).thenReturn(
            GitHubCommitResponse(sha = "sha-profesor-111")
        )
        `when`(gitHubRepoService.getRepositoryTree("template-nuevo", "sha-profesor-111", true)).thenReturn(
            GitHubGitTreeResponse(
                sha = "sha-profesor-111",
                tree = listOf(
                    GitHubTreeItem(path = "pom.xml", type = "blob", sha = "blob-pom-1"),
                    GitHubTreeItem(path = ".github/workflows/ci.yml", type = "blob", sha = "blob-ci-viejo"),
                )
            )
        )
        `when`(gitHubRepoService.getFileContent("template-nuevo", "pom.xml")).thenReturn(
            GitHubFileContentResponse(
                name = "pom.xml",
                path = "pom.xml",
                sha = "blob-pom-1",
                content = encodeBase64(pomSinCalidad),
            )
        )

        `when`(
            gitHubRepoService.createOrUpdateFile(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any(),
                any(),
                any()
            )
        ).thenAnswer { invocation ->
            val pathArg = invocation.getArgument<String>(1)
            GitHubContentUpdateResponse(
                commit = GitHubContentCommitInfo(sha = "sha-after-$pathArg")
            )
        }
        `when`(repositorioRepository.save(anyRepositorio())).thenAnswer { it.getArgument(0) }

        val resultado = service.asegurarTemplateEstandarizado("template-nuevo")

        assertTrue(resultado.templateAprobado)
        assertEquals("sha-after-.github/workflows/ci.yml", resultado.ultimoCommitSha)

        // Verifica parcheo de pom.xml con su sha original
        verify(gitHubRepoService).createOrUpdateFile(
            eqNonNull("template-nuevo"),
            eqNonNull("pom.xml"),
            matchString { content ->
                content.contains("jacoco-maven-plugin") &&
                    content.contains("maven-checkstyle-plugin") &&
                    content.contains("junit-jupiter")
            },
            anyString(),
            eq("blob-pom-1"),
            isNull(),
            isNull()
        )

        // Verifica inyección de Maven Wrapper
        verify(gitHubRepoService).createOrUpdateFile(
            eqNonNull("template-nuevo"),
            eqNonNull(".mvn/wrapper/maven-wrapper.properties"),
            anyString(),
            anyString(),
            isNull(),
            isNull(),
            isNull()
        )
        verify(gitHubRepoService).createOrUpdateFile(
            eqNonNull("template-nuevo"),
            eqNonNull("mvnw"),
            anyString(),
            anyString(),
            isNull(),
            isNull(),
            isNull()
        )
        verify(gitHubRepoService).createOrUpdateFile(
            eqNonNull("template-nuevo"),
            eqNonNull("mvnw.cmd"),
            anyString(),
            anyString(),
            isNull(),
            isNull(),
            isNull()
        )

        // Verifica inyección de clase y test de rescate porque no había archivos en src/test/
        verify(gitHubRepoService).createOrUpdateFile(
            eqNonNull("template-nuevo"),
            eqNonNull("src/main/java/Sanity.java"),
            matchString { it.contains("class Sanity") && it.contains("boolean check()") },
            anyString(),
            isNull(),
            isNull(),
            isNull()
        )
        verify(gitHubRepoService).createOrUpdateFile(
            eqNonNull("template-nuevo"),
            eqNonNull("src/test/java/SanityTest.java"),
            matchString { it.contains("Sanity sanity = new Sanity()") && it.contains("assertTrue(sanity.check())") },
            anyString(),
            isNull(),
            isNull(),
            isNull()
        )

        // Verifica inyección forzosa de .github/workflows/ci.yml pisando el blob-ci-viejo
        verify(gitHubRepoService).createOrUpdateFile(
            eqNonNull("template-nuevo"),
            eqNonNull(".github/workflows/ci.yml"),
            matchString { content ->
                content.contains("name: CI Linter & Tests") &&
                    content.contains("X-GitHub-Event: linter_report") &&
                    content.contains("X-GitHub-Event: coverage_report") &&
                    content.contains("https://webhook.unqlassroom.edu.ar/events")
            },
            anyString(),
            eq("blob-ci-viejo"),
            isNull(),
            isNull()
        )
    }

    @Test
    fun `asegurarTemplateEstandarizado respeta tests existentes y Gradle Wrapper existente sin duplicarlos`() {
        val gradleContentCompleto = """
            plugins {
                id 'java'
                id 'jacoco'
                id 'checkstyle'
            }
        """.trimIndent()

        `when`(repositorioRepository.findByNombreIgnoreCase("template-gradle")).thenReturn(null)
        `when`(gitHubRepoService.getUltimoCommit("template-gradle")).thenReturn(
            GitHubCommitResponse(sha = "sha-gradle-1")
        )
        `when`(gitHubRepoService.getRepositoryTree("template-gradle", "sha-gradle-1", true)).thenReturn(
            GitHubGitTreeResponse(
                sha = "sha-gradle-1",
                tree = listOf(
                    GitHubTreeItem(path = "build.gradle", type = "blob", sha = "sha-bg"),
                    GitHubTreeItem(path = "gradlew", type = "blob", sha = "sha-gw"),
                    GitHubTreeItem(path = "gradlew.bat", type = "blob", sha = "sha-gw-bat"),
                    GitHubTreeItem(path = "gradle/wrapper/gradle-wrapper.properties", type = "blob", sha = "sha-gwp"),
                    GitHubTreeItem(path = "src/test/java/ar/edu/unq/CalculadoraTest.java", type = "blob", sha = "sha-test"),
                )
            )
        )
        `when`(gitHubRepoService.getFileContent("template-gradle", "build.gradle")).thenReturn(
            GitHubFileContentResponse(
                name = "build.gradle",
                path = "build.gradle",
                sha = "sha-bg",
                content = encodeBase64(gradleContentCompleto),
            )
        )
        `when`(
            gitHubRepoService.createOrUpdateFile(
                eqNonNull("template-gradle"),
                eqNonNull(".github/workflows/ci.yml"),
                anyString(),
                anyString(),
                isNull(),
                isNull(),
                isNull()
            )
        ).thenReturn(
            GitHubContentUpdateResponse(commit = GitHubContentCommitInfo(sha = "sha-ci-final"))
        )
        `when`(repositorioRepository.save(anyRepositorio())).thenAnswer { it.getArgument(0) }

        val resultado = service.asegurarTemplateEstandarizado("template-gradle")

        assertTrue(resultado.esTemplate)
        assertTrue(resultado.templateAprobado)
        assertEquals("sha-ci-final", resultado.ultimoCommitSha)

        // Únicamente debe escribir el workflow de CI forzoso; no debe tocar build.gradle, wrappers ni SanityTest
        verify(gitHubRepoService, times(1)).createOrUpdateFile(
            anyString(),
            anyString(),
            anyString(),
            anyString(),
            any(),
            any(),
            any()
        )
        verify(gitHubRepoService).createOrUpdateFile(
            eqNonNull("template-gradle"),
            eqNonNull(".github/workflows/ci.yml"),
            anyString(),
            anyString(),
            isNull(),
            isNull(),
            isNull()
        )
    }

    @Test
    fun `analizarYParchearPom es idempotente cuando el pom ya contiene JaCoCo, Checkstyle y JUnit`() {
        val pomCompleto = TemplateStandardizationServiceImpl.DEFAULT_POM_XML
        val resultado = service.analizarYParchearPom(pomCompleto)
        assertNull(resultado)
    }

    @Test
    fun `analizarYParchearGradle agrega bloques de JaCoCo y Checkstyle en Groovy y Kotlin DSL`() {
        val groovySimple = "plugins {\n    id 'java'\n}\n"
        val groovyParcheado = service.analizarYParchearGradle(groovySimple, esKotlinDsl = false)
        assertNotNull(groovyParcheado)
        assertTrue(groovyParcheado!!.contains("apply plugin: 'jacoco'"))
        assertTrue(groovyParcheado.contains("apply plugin: 'checkstyle'"))
        assertNull(service.analizarYParchearGradle(groovyParcheado, esKotlinDsl = false))

        val ktsSimple = "plugins {\n    java\n}\n"
        val ktsParcheado = service.analizarYParchearGradle(ktsSimple, esKotlinDsl = true)
        assertNotNull(ktsParcheado)
        assertTrue(ktsParcheado!!.contains("apply(plugin = \"jacoco\")"))
        assertTrue(ktsParcheado.contains("apply(plugin = \"checkstyle\")"))
        assertNull(service.analizarYParchearGradle(ktsParcheado, esKotlinDsl = true))
    }

    @Test
    fun `PushWebhookHandler al recibir push sobre un repositorio template actualiza ultimoCommitSha y pone templateAprobado en false`() {
        val objectMapper = ObjectMapper()
        val handler = PushWebhookHandler(
            repositorioRepository = repositorioRepository,
            objectMapper = objectMapper,
            commitAnalysisService = null,
        )

        val repoTemplate = Repositorio(
            id = 50L,
            nombre = "tp1-template",
            htmlUrl = "https://github.com/UNQlassroom/tp1-template",
            esTemplate = true,
            ultimoCommitSha = "sha-anterior-aprobado",
            templateAprobado = true,
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("tp1-template")).thenReturn(repoTemplate)
        `when`(repositorioRepository.save(anyRepositorio())).thenAnswer { it.getArgument(0) }

        val payload = """
            {
              "after": "nuevo-sha-del-profesor-999",
              "repository": {
                "name": "tp1-template"
              },
              "head_commit": {
                "id": "nuevo-sha-del-profesor-999",
                "message": "feat: actualizar consigna TP1",
                "timestamp": "2026-10-29T19:00:00Z"
              }
            }
        """.trimIndent()

        val event = WebhookEvent(
            eventType = "push",
            payload = payload,
        )

        handler.handle(event)

        assertEquals("feat: actualizar consigna TP1", repoTemplate.ultimoCommit)
        assertEquals("nuevo-sha-del-profesor-999", repoTemplate.ultimoCommitSha)
        assertFalse(repoTemplate.templateAprobado)
        verify(repositorioRepository).save(repoTemplate)
    }

    @Test
    fun `GitHubRepoService createOrUpdateFile reintenta con sha existente si GitHub devuelve 422 sin sha`() {
        val mockClient = mock(GitHubAppClient::class.java)
        val realRepoService = GitHubRepoService(mockClient, gitHubAppProperties)

        val calledBodies = mutableListOf<String>()
        `when`(
            mockClient.executeInstallationRequest(
                eqNonNull("PUT"),
                eqNonNull("/repos/UNQlassroom/mi-repo/contents/.github/workflows/ci.yml"),
                eqNonNull(GitHubContentUpdateResponse::class.java),
                anyString()
            )
        ).thenAnswer { invocation ->
            val body = invocation.getArgument<String>(3)
            calledBodies.add(body)
            if (calledBodies.size == 1) {
                throw GitHubApiException(422, "Invalid request. \"sha\" wasn't supplied.")
            }
            GitHubContentUpdateResponse(commit = GitHubContentCommitInfo(sha = "commit-sha-2"))
        }

        `when`(
            mockClient.executeInstallationRequest(
                eqNonNull("GET"),
                eqNonNull("/repos/UNQlassroom/mi-repo/contents/.github/workflows/ci.yml"),
                eqNonNull(GitHubFileContentResponse::class.java),
                isNull()
            )
        ).thenReturn(
            GitHubFileContentResponse(
                name = "ci.yml",
                path = ".github/workflows/ci.yml",
                sha = "existing-blob-sha-777",
            )
        )

        val response = realRepoService.createOrUpdateFile(
            repoName = "mi-repo",
            path = ".github/workflows/ci.yml",
            content = "name: CI",
            message = "ci: update workflow",
            sha = null,
        )

        assertEquals("commit-sha-2", response.commit?.sha)
        assertEquals(2, calledBodies.size)
        assertFalse(calledBodies[0].contains("existing-blob-sha-777"))
        assertTrue(calledBodies[1].contains("existing-blob-sha-777"))
    }
}
