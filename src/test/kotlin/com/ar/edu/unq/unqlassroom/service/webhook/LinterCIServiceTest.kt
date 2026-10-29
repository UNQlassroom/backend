package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.GrupoAsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.ReporteLinterRepository
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class LinterCIServiceTest {

    @Mock
    private lateinit var reporteLinterRepository: ReporteLinterRepository

    @Mock
    private lateinit var grupoAsignacionRepository: GrupoAsignacionRepository

    @Mock
    private lateinit var usuarioRepository: UsuarioRepository

    private val objectMapper = ObjectMapper()
    private lateinit var service: LinterCIService

    @BeforeEach
    fun setUp() {
        service = LinterCIService(
            reporteLinterRepository = reporteLinterRepository,
            grupoAsignacionRepository = grupoAsignacionRepository,
            usuarioRepository = usuarioRepository,
        )
    }

    @Test
    fun `procesarReporteDeEvento extrae y persiste incidencias desde JSON directo asociando alumno grupo y asignacion`() {
        val alumno = Usuario(id = 10L, username = "juanperez", esDocente = false)
        val repo = Repositorio(
            id = 5L,
            nombre = "tp1-juanperez",
            htmlUrl = "https://github.com/UNQlassroom/tp1-juanperez",
            ultimoCommit = "sha-default",
        )
        val asignacion = Asignacion(
            id = 20L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tp1-template",
        )
        val grupo = GrupoAsignacion(
            id = 30L,
            asignacion = asignacion,
            repositorio = repo,
            integrantes = mutableListOf(alumno),
        )

        val payload = """
        {
          "repository": { "name": "tp1-juanperez" },
          "commit_sha": "abc987654",
          "run_id": 445566,
          "author": "juanperez",
          "linter": {
            "herramienta": "ktlint",
            "incidencias": [
              {
                "archivo": "/home/runner/work/tp1-juanperez/tp1-juanperez/src/main/kotlin/Calculadora.kt",
                "linea": 4,
                "columna": 1,
                "severidad": "ERROR",
                "regla": "unused import",
                "mensaje": "Unused import 'java.util.Date'"
              },
              {
                "archivo": "src/main/kotlin/Calculadora.kt",
                "linea": 25,
                "columna": 10,
                "severidad": "WARNING",
                "regla": "MagicNumber",
                "mensaje": "Avoid magic numbers"
              }
            ]
          }
        }
        """.trimIndent()

        `when`(grupoAsignacionRepository.findByRepositorioNombreIgnoreCase("tp1-juanperez")).thenReturn(grupo)
        `when`(reporteLinterRepository.findByRepositorioIdAndCommitSha(5L, "abc987654")).thenReturn(null)
        `when`(reporteLinterRepository.save(any(ReporteLinter::class.java))).thenAnswer { it.arguments[0] as ReporteLinter }

        val resultado = service.procesarReporteDeEvento(repo, objectMapper.readTree(payload))

        assertNotNull(resultado)
        val captor = ArgumentCaptor.forClass(ReporteLinter::class.java)
        verify(reporteLinterRepository).save(captor.capture())

        val guardado = captor.value
        assertEquals("abc987654", guardado.commitSha)
        assertEquals("ktlint", guardado.herramienta)
        assertEquals(1, guardado.cantidadErrores)
        assertEquals(1, guardado.cantidadAdvertencias)
        assertEquals(2, guardado.totalIncidencias)
        assertEquals(2, guardado.incidencias.size)
        assertEquals("src/main/kotlin/Calculadora.kt", guardado.incidencias[0].archivo)
        assertEquals(4, guardado.incidencias[0].linea)
        assertEquals(SeveridadLinter.ERROR, guardado.incidencias[0].severidad)
        assertEquals("unused import", guardado.incidencias[0].regla)
        assertEquals(alumno, guardado.alumno)
        assertEquals(grupo, guardado.grupoAsignacion)
        assertEquals(asignacion, guardado.asignacion)
        assertEquals(445566L, guardado.runId)
    }

    @Test
    fun `procesarReporteDeEvento parsea formato por archivo estilo ESLint y ktlint JSON`() {
        val repo = Repositorio(
            id = 6L,
            nombre = "tp2-eslint",
            htmlUrl = "https://github.com/UNQlassroom/tp2-eslint",
        )

        val payload = """
        {
          "repo_name": "tp2-eslint",
          "commit_sha": "sha-eslint-1",
          "eslint": [
            {
              "filePath": "/github/workspace/src/components/Header.tsx",
              "messages": [
                {
                  "ruleId": "no-unused-vars",
                  "severity": 2,
                  "message": "'unusedVar' is defined but never used.",
                  "line": 12,
                  "column": 7
                },
                {
                  "ruleId": "semi",
                  "severity": 1,
                  "message": "Missing semicolon.",
                  "line": 19,
                  "column": 24
                }
              ]
            }
          ]
        }
        """.trimIndent()

        `when`(reporteLinterRepository.findByRepositorioIdAndCommitSha(6L, "sha-eslint-1")).thenReturn(null)
        `when`(reporteLinterRepository.save(any(ReporteLinter::class.java))).thenAnswer { it.arguments[0] as ReporteLinter }

        val resultado = service.procesarReporteDeEvento(repo, objectMapper.readTree(payload))

        assertNotNull(resultado)
        assertEquals("eslint", resultado?.herramienta)
        assertEquals(1, resultado?.cantidadErrores)
        assertEquals(1, resultado?.cantidadAdvertencias)
        assertEquals(2, resultado?.totalIncidencias)
        assertEquals("src/components/Header.tsx", resultado?.incidencias?.get(0)?.archivo)
        assertEquals("no-unused-vars", resultado?.incidencias?.get(0)?.regla)
        assertEquals(SeveridadLinter.ERROR, resultado?.incidencias?.get(0)?.severidad)
        assertEquals("semi", resultado?.incidencias?.get(1)?.regla)
        assertEquals(SeveridadLinter.WARNING, resultado?.incidencias?.get(1)?.severidad)
    }

    @Test
    fun `procesarReporteDeEvento parsea formato SARIF 2_1_0 correctamente`() {
        val repo = Repositorio(
            id = 7L,
            nombre = "tp-sarif",
            htmlUrl = "https://github.com/UNQlassroom/tp-sarif",
        )

        val payload = """
        {
          "repo_name": "tp-sarif",
          "commit_sha": "sha-sarif-99",
          "sarif": {
            "runs": [
              {
                "results": [
                  {
                    "ruleId": "detekt.UnusedPrivateMember",
                    "level": "warning",
                    "message": { "text": "Private function 'helper' is unused." },
                    "locations": [
                      {
                        "physicalLocation": {
                          "artifactLocation": { "uri": "src/main/kotlin/Utils.kt" },
                          "region": { "startLine": 33, "startColumn": 5 }
                        }
                      }
                    ]
                  },
                  {
                    "ruleId": "detekt.MaxLineLength",
                    "level": "error",
                    "message": { "text": "Line is too long." },
                    "locations": [
                      {
                        "physicalLocation": {
                          "artifactLocation": { "uri": "src/main/kotlin/Utils.kt" },
                          "region": { "startLine": 50, "startColumn": 1 }
                        }
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
        """.trimIndent()

        `when`(reporteLinterRepository.findByRepositorioIdAndCommitSha(7L, "sha-sarif-99")).thenReturn(null)
        `when`(reporteLinterRepository.save(any(ReporteLinter::class.java))).thenAnswer { it.arguments[0] as ReporteLinter }

        val resultado = service.procesarReporteDeEvento(repo, objectMapper.readTree(payload))

        assertNotNull(resultado)
        assertEquals(1, resultado?.cantidadErrores)
        assertEquals(1, resultado?.cantidadAdvertencias)
        assertEquals(2, resultado?.incidencias?.size)
        assertEquals("UnusedPrivateMember", resultado?.incidencias?.get(0)?.regla)
        assertEquals(33, resultado?.incidencias?.get(0)?.linea)
        assertEquals(SeveridadLinter.WARNING, resultado?.incidencias?.get(0)?.severidad)
        assertEquals("MaxLineLength", resultado?.incidencias?.get(1)?.regla)
        assertEquals(50, resultado?.incidencias?.get(1)?.linea)
        assertEquals(SeveridadLinter.ERROR, resultado?.incidencias?.get(1)?.severidad)
    }

    @Test
    fun `procesarReporteDeEvento parsea formato Checkstyle o Detekt XML embebido sin descargar artifacts`() {
        val repo = Repositorio(
            id = 8L,
            nombre = "tp-checkstyle",
            htmlUrl = "https://github.com/UNQlassroom/tp-checkstyle",
        )

        val checkstyleXml = """
            <checkstyle version="8.0">
              <file name="/home/runner/work/tp-checkstyle/tp-checkstyle/src/main/java/App.java">
                <error line="8" column="1" severity="error" message="Unused import - java.util.List." source="com.puppycrawl.tools.checkstyle.checks.imports.UnusedImportsCheck"/>
                <error line="15" column="5" severity="warning" message="Missing a Javadoc comment." source="com.puppycrawl.tools.checkstyle.checks.javadoc.JavadocMethodCheck"/>
              </file>
            </checkstyle>
        """.trimIndent()

        val payloadNode = objectMapper.createObjectNode().apply {
            put("repo_name", "tp-checkstyle")
            put("commit_sha", "sha-xml-1")
            put("checkstyle_xml", checkstyleXml)
        }

        `when`(reporteLinterRepository.findByRepositorioIdAndCommitSha(8L, "sha-xml-1")).thenReturn(null)
        `when`(reporteLinterRepository.save(any(ReporteLinter::class.java))).thenAnswer { it.arguments[0] as ReporteLinter }

        val resultado = service.procesarReporteDeEvento(repo, payloadNode)

        assertNotNull(resultado)
        assertEquals("checkstyle", resultado?.herramienta)
        assertEquals(1, resultado?.cantidadErrores)
        assertEquals(1, resultado?.cantidadAdvertencias)
        assertEquals(2, resultado?.incidencias?.size)
        assertEquals("src/main/java/App.java", resultado?.incidencias?.get(0)?.archivo)
        assertEquals(8, resultado?.incidencias?.get(0)?.linea)
        assertEquals("UnusedImports", resultado?.incidencias?.get(0)?.regla)
        assertEquals(SeveridadLinter.ERROR, resultado?.incidencias?.get(0)?.severidad)
        assertEquals("JavadocMethod", resultado?.incidencias?.get(1)?.regla)
        assertEquals(SeveridadLinter.WARNING, resultado?.incidencias?.get(1)?.severidad)
    }

    @Test
    fun `procesarReporteDeEvento actualiza registro existente si ya existe para el mismo repositorio y commit_sha`() {
        val repo = Repositorio(
            id = 9L,
            nombre = "tp-idempotente",
            htmlUrl = "https://github.com/UNQlassroom/tp-idempotente",
        )
        val existente = ReporteLinter(
            id = 500L,
            commitSha = "sha-repetido",
            cantidadErrores = 3,
            cantidadAdvertencias = 2,
            totalIncidencias = 5,
            repositorio = repo,
        )
        existente.reemplazarIncidencias(
            listOf(IncidenciaLinter(archivo = "src/Viejo.kt", linea = 1, severidad = SeveridadLinter.ERROR, regla = "old-rule"))
        )

        val payload = """
        {
          "repo_name": "tp-idempotente",
          "commit_sha": "sha-repetido",
          "linter": {
            "incidencias": [
              {
                "archivo": "src/Nuevo.kt",
                "linea": 14,
                "severidad": "WARNING",
                "regla": "unused import",
                "mensaje": "Import no utilizado"
              }
            ]
          }
        }
        """.trimIndent()

        `when`(reporteLinterRepository.findByRepositorioIdAndCommitSha(9L, "sha-repetido")).thenReturn(existente)
        `when`(reporteLinterRepository.save(any(ReporteLinter::class.java))).thenAnswer { it.arguments[0] as ReporteLinter }

        val actualizado = service.procesarReporteDeEvento(repo, objectMapper.readTree(payload))

        assertNotNull(actualizado)
        assertEquals(500L, actualizado?.id)
        assertEquals(0, actualizado?.cantidadErrores)
        assertEquals(1, actualizado?.cantidadAdvertencias)
        assertEquals(1, actualizado?.totalIncidencias)
        assertEquals(1, actualizado?.incidencias?.size)
        assertEquals("src/Nuevo.kt", actualizado?.incidencias?.first()?.archivo)
    }

    @Test
    fun `procesarReporteDeEvento retorna null y no interactua con el repositorio si el payload no contiene datos de linter`() {
        val repo = Repositorio(
            id = 10L,
            nombre = "tp-sin-linter",
            htmlUrl = "https://github.com/UNQlassroom/tp-sin-linter",
        )
        val payload = """
        {
          "action": "completed",
          "repository": { "name": "tp-sin-linter" },
          "workflow_run": {
            "status": "completed",
            "conclusion": "success"
          }
        }
        """.trimIndent()

        val resultado = service.procesarReporteDeEvento(repo, objectMapper.readTree(payload))

        assertNull(resultado)
        verifyNoInteractions(reporteLinterRepository)
    }
}

