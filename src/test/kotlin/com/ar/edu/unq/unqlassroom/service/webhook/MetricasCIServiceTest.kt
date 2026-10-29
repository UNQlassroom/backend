package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.GrupoAsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.MetricaCIRepository
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
class MetricasCIServiceTest {

    @Mock
    private lateinit var metricaCIRepository: MetricaCIRepository

    @Mock
    private lateinit var grupoAsignacionRepository: GrupoAsignacionRepository

    @Mock
    private lateinit var usuarioRepository: UsuarioRepository

    private val objectMapper = ObjectMapper()
    private lateinit var metricasCIService: MetricasCIService

    @BeforeEach
    fun setUp() {
        metricasCIService = MetricasCIService(
            metricaCIRepository = metricaCIRepository,
            grupoAsignacionRepository = grupoAsignacionRepository,
            usuarioRepository = usuarioRepository,
            umbralCoberturaDefault = 80.0,
        )
    }

    @Test
    fun `procesarMetricasDeEvento retorna null cuando el payload no contiene datos de metricas`() {
        val repo = Repositorio(id = 1L, nombre = "tp1-juan", htmlUrl = "https://github.com/org/tp1-juan")
        val payload = objectMapper.readTree(
            """
            {
              "action": "completed",
              "repository": { "name": "tp1-juan" },
              "workflow_run": {
                "id": 123,
                "status": "completed",
                "conclusion": "success"
              }
            }
            """.trimIndent()
        )

        val resultado = metricasCIService.procesarMetricasDeEvento(repo, payload)

        assertNull(resultado)
        verifyNoInteractions(metricaCIRepository)
    }

    @Test
    fun `procesarMetricasDeEvento calcula porcentaje de cobertura desde contadores JaCoCo y registra tests JUnit`() {
        val alumno = Usuario(id = 10L, username = "juanperez", esDocente = false)
        val asignacion = Asignacion(
            id = 20L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl-tp1"
        )
        val repo = Repositorio(id = 1L, nombre = "tp1-juanperez", htmlUrl = "https://github.com/org/tp1-juanperez")
        val grupo = GrupoAsignacion(
            id = 30L,
            asignacion = asignacion,
            repositorio = repo,
            integrantes = mutableListOf(alumno)
        )

        `when`(grupoAsignacionRepository.findByRepositorioNombreIgnoreCase("tp1-juanperez")).thenReturn(grupo)
        `when`(metricaCIRepository.findByRepositorioIdAndCommitSha(1L, "a1b2c3d4")).thenReturn(null)
        `when`(metricaCIRepository.save(any(MetricaCI::class.java))).thenAnswer { it.arguments[0] }

        val payload = objectMapper.readTree(
            """
            {
              "repository": { "name": "tp1-juanperez" },
              "commit_sha": "a1b2c3d4",
              "run_id": 555,
              "coverage": {
                "lines_covered": 85,
                "lines_missed": 15
              },
              "tests": {
                "tests_run": 20,
                "tests_failed": 1,
                "tests_errors": 1,
                "tests_skipped": 2
              }
            }
            """.trimIndent()
        )

        val guardada = metricasCIService.procesarMetricasDeEvento(repo, payload)

        assertNotNull(guardada)
        assertEquals("a1b2c3d4", guardada!!.commitSha)
        assertEquals(85.0, guardada.coberturaLineas)
        assertEquals(85, guardada.lineasCubiertas)
        assertEquals(15, guardada.lineasPerdidas)
        assertEquals(80.0, guardada.umbralCobertura)
        assertTrue(guardada.cumpleUmbralCobertura)
        assertEquals(20, guardada.testsEjecutados)
        assertEquals(16, guardada.testsPasados)
        assertEquals(2, guardada.testsFallados)
        assertEquals(2, guardada.testsSalteados)
        assertEquals(555L, guardada.runId)
        assertEquals("juanperez", guardada.autorUsername)
        assertEquals(alumno, guardada.alumno)
        assertEquals(grupo, guardada.grupoAsignacion)
        assertEquals(asignacion, guardada.asignacion)
    }

    @Test
    fun `procesarMetricasDeEvento parsea reportes XML de JaCoCo y JUnit correctamente`() {
        val alumno = Usuario(id = 11L, username = "maria", esDocente = false)
        val repo = Repositorio(id = 2L, nombre = "tp1-maria", htmlUrl = "https://github.com/org/tp1-maria")
        val grupo = GrupoAsignacion(id = 31L, repositorio = repo, integrantes = mutableListOf(alumno))

        `when`(grupoAsignacionRepository.findByRepositorioNombreIgnoreCase("tp1-maria")).thenReturn(grupo)
        `when`(metricaCIRepository.findByRepositorioIdAndCommitSha(2L, "sha-xml-1")).thenReturn(null)
        `when`(metricaCIRepository.save(any(MetricaCI::class.java))).thenAnswer { it.arguments[0] }

        val jacocoXml = """
            <report name="tp1">
                <package name="ar/unq">
                    <counter type="LINE" missed="5" covered="20"/>
                </package>
                <counter type="INSTRUCTION" missed="10" covered="90"/>
                <counter type="LINE" missed="25" covered="75"/>
            </report>
        """.trimIndent()

        val junitXml = """
            <testsuites>
                <testsuite name="SuiteA" tests="10" failures="1" errors="0" skipped="1">
                </testsuite>
                <testsuite name="SuiteB" tests="5" failures="0" errors="1" skipped="0">
                </testsuite>
            </testsuites>
        """.trimIndent()

        val node = objectMapper.createObjectNode().apply {
            put("repo_name", "tp1-maria")
            put("commit_sha", "sha-xml-1")
            put("jacoco_xml", jacocoXml)
            put("junit_xml", junitXml)
        }

        val guardada = metricasCIService.procesarMetricasDeEvento(repo, node)

        assertNotNull(guardada)
        assertEquals(75.0, guardada!!.coberturaLineas)
        assertEquals(75, guardada.lineasCubiertas)
        assertEquals(25, guardada.lineasPerdidas)
        assertFalse(guardada.cumpleUmbralCobertura)
        assertEquals(15, guardada.testsEjecutados)
        assertEquals(12, guardada.testsPasados)
        assertEquals(2, guardada.testsFallados)
        assertEquals(1, guardada.testsSalteados)
    }

    @Test
    fun `procesarMetricasDeEvento realiza upsert actualizando la metrica existente cuando se re-ejecuta CI en el mismo commit`() {
        val repo = Repositorio(id = 5L, nombre = "tp1-grupo1", htmlUrl = "https://github.com/org/tp1-grupo1")
        val metricaExistente = MetricaCI(
            id = 99L,
            commitSha = "same-commit-sha",
            coberturaLineas = 50.0,
            lineasCubiertas = 50,
            lineasPerdidas = 50,
            umbralCobertura = 80.0,
            cumpleUmbralCobertura = false,
            testsEjecutados = 10,
            testsPasados = 8,
            testsFallados = 2,
            testsSalteados = 0,
            repositorio = repo,
        )

        `when`(grupoAsignacionRepository.findByRepositorioNombreIgnoreCase("tp1-grupo1")).thenReturn(null)
        `when`(metricaCIRepository.findByRepositorioIdAndCommitSha(5L, "same-commit-sha")).thenReturn(metricaExistente)
        `when`(metricaCIRepository.save(any(MetricaCI::class.java))).thenAnswer { it.arguments[0] }

        val payloadReRun = objectMapper.readTree(
            """
            {
              "repo_name": "tp1-grupo1",
              "commit_sha": "same-commit-sha",
              "coberturaLineas": 92.5,
              "lineasCubiertas": 185,
              "lineasPerdidas": 15,
              "testsEjecutados": 10,
              "testsPasados": 10,
              "testsFallados": 0,
              "testsSalteados": 0,
              "runId": 777
            }
            """.trimIndent()
        )

        val actualizada = metricasCIService.procesarMetricasDeEvento(repo, payloadReRun)

        assertNotNull(actualizada)
        assertEquals(99L, actualizada!!.id)
        assertEquals(92.5, actualizada.coberturaLineas)
        assertEquals(185, actualizada.lineasCubiertas)
        assertEquals(15, actualizada.lineasPerdidas)
        assertTrue(actualizada.cumpleUmbralCobertura)
        assertEquals(10, actualizada.testsPasados)
        assertEquals(0, actualizada.testsFallados)
        assertEquals(777L, actualizada.runId)

        val captor = ArgumentCaptor.forClass(MetricaCI::class.java)
        verify(metricaCIRepository, times(1)).save(captor.capture())
        assertEquals(99L, captor.value.id)
    }

    @Test
    fun `procesarMetricasDeEvento resuelve el alumno integrante del grupo por autor del workflow_run`() {
        val a1 = Usuario(id = 1L, username = "integrante1", esDocente = false)
        val a2 = Usuario(id = 2L, username = "integrante2", esDocente = false)
        val repo = Repositorio(id = 10L, nombre = "tp-grupal-g1", htmlUrl = "https://github.com/org/tp-grupal-g1")
        val grupo = GrupoAsignacion(
            id = 40L,
            nombre = "Grupo 1",
            repositorio = repo,
            integrantes = mutableListOf(a1, a2),
        )

        `when`(grupoAsignacionRepository.findByRepositorioNombreIgnoreCase("tp-grupal-g1")).thenReturn(grupo)
        `when`(metricaCIRepository.findByRepositorioIdAndCommitSha(10L, "sha-grupal")).thenReturn(null)
        `when`(metricaCIRepository.save(any(MetricaCI::class.java))).thenAnswer { it.arguments[0] }

        val payload = objectMapper.readTree(
            """
            {
              "repository": { "name": "tp-grupal-g1" },
              "workflow_run": {
                "id": 888,
                "head_sha": "sha-grupal",
                "actor": { "login": "INTEGRANTE2" },
                "metrics": {
                  "line_coverage": "81.25%",
                  "tests_passed": 15,
                  "tests_failed": 0,
                  "tests_skipped": 1
                }
              }
            }
            """.trimIndent()
        )

        val guardada = metricasCIService.procesarMetricasDeEvento(repo, payload)

        assertNotNull(guardada)
        assertEquals(81.25, guardada!!.coberturaLineas)
        assertTrue(guardada.cumpleUmbralCobertura)
        assertEquals(16, guardada.testsEjecutados)
        assertEquals(15, guardada.testsPasados)
        assertEquals(0, guardada.testsFallados)
        assertEquals(1, guardada.testsSalteados)
        assertEquals(a2, guardada.alumno)
    }
}

