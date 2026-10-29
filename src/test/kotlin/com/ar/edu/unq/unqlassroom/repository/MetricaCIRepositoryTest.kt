package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@SpringBootTest
@Transactional
class MetricaCIRepositoryTest {

    @Autowired
    private lateinit var metricaCIRepository: MetricaCIRepository

    @Autowired
    private lateinit var repositorioRepository: RepositorioRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Autowired
    private lateinit var cursoRepository: CursoRepository

    @Autowired
    private lateinit var asignacionRepository: AsignacionRepository

    @Autowired
    private lateinit var grupoAsignacionRepository: GrupoAsignacionRepository

    @Test
    fun `persiste correctamente metricas asociadas a alumno repositorio grupo y asignacion`() {
        val docente = usuarioRepository.save(Usuario(username = "docente_metricas", esDocente = true))
        val alumno = usuarioRepository.save(Usuario(username = "alumno_metricas", esDocente = false))
        val curso = cursoRepository.save(
            Curso(materia = "Ingeniería de Software", anio = 2026, semestre = 2, comision = 1, owner = docente)
        )
        val asignacion = asignacionRepository.save(
            Asignacion(
                titulo = "TP1 - Cobertura",
                tipo = TipoAsignacion.INDIVIDUAL,
                templateRepoName = "tmpl-tp1",
                curso = curso,
            )
        )
        val repo = repositorioRepository.save(
            Repositorio(
                nombre = "2026s2_c1_ing_tp1_alumno_metricas",
                htmlUrl = "https://github.com/UNQlassroom/2026s2_c1_ing_tp1_alumno_metricas",
            )
        )
        val grupo = grupoAsignacionRepository.save(
            GrupoAsignacion(
                nombre = null,
                asignacion = asignacion,
                repositorio = repo,
                integrantes = mutableListOf(alumno),
            )
        )

        val metrica = metricaCIRepository.save(
            MetricaCI(
                commitSha = "abc123456",
                coberturaLineas = 85.5,
                lineasCubiertas = 171,
                lineasPerdidas = 29,
                umbralCobertura = 80.0,
                cumpleUmbralCobertura = true,
                testsEjecutados = 25,
                testsPasados = 23,
                testsFallados = 1,
                testsSalteados = 1,
                autorUsername = alumno.username,
                runId = 9001L,
                repositorio = repo,
                grupoAsignacion = grupo,
                asignacion = asignacion,
                alumno = alumno,
            )
        )

        assertNotNull(metrica.id)

        val porRepo = metricaCIRepository.findByRepositorioIdOrderByFechaActualizacionDesc(repo.id!!)
        val porGrupo = metricaCIRepository.findByGrupoAsignacionIdOrderByFechaActualizacionDesc(grupo.id!!)
        val porAsignacion = metricaCIRepository.findByAsignacionIdOrderByFechaActualizacionDesc(asignacion.id!!)
        val porAlumno = metricaCIRepository.findByAlumnoIdOrderByFechaActualizacionDesc(alumno.id!!)

        assertEquals(1, porRepo.size)
        assertEquals(1, porGrupo.size)
        assertEquals(1, porAsignacion.size)
        assertEquals(1, porAlumno.size)

        val recuperada = porRepo.first()
        assertEquals("abc123456", recuperada.commitSha)
        assertEquals(85.5, recuperada.coberturaLineas)
        assertEquals(171, recuperada.lineasCubiertas)
        assertEquals(29, recuperada.lineasPerdidas)
        assertTrue(recuperada.cumpleUmbralCobertura)
        assertEquals(25, recuperada.testsEjecutados)
        assertEquals(23, recuperada.testsPasados)
        assertEquals(1, recuperada.testsFallados)
        assertEquals(1, recuperada.testsSalteados)
        assertEquals(alumno.id, recuperada.alumno?.id)
        assertEquals(asignacion.id, recuperada.asignacion?.id)
        assertEquals(grupo.id, recuperada.grupoAsignacion?.id)
    }

    @Test
    fun `asegura integridad ante multiples ejecuciones de CI en un mismo commit actualizando u obligando unicidad`() {
        val repo = repositorioRepository.save(
            Repositorio(nombre = "repo-unicidad-ci", htmlUrl = "https://github.com/UNQlassroom/repo-unicidad-ci")
        )

        val primeraEjecucion = metricaCIRepository.saveAndFlush(
            MetricaCI(
                commitSha = "sha-unico-1",
                coberturaLineas = 70.0,
                lineasCubiertas = 70,
                lineasPerdidas = 30,
                umbralCobertura = 80.0,
                cumpleUmbralCobertura = false,
                testsEjecutados = 10,
                testsPasados = 9,
                testsFallados = 1,
                testsSalteados = 0,
                repositorio = repo,
            )
        )

        val encontrada = metricaCIRepository.findByRepositorioIdAndCommitSha(repo.id!!, "sha-unico-1")
        assertNotNull(encontrada)
        assertEquals(primeraEjecucion.id, encontrada?.id)

        encontrada!!.actualizarDesdeReporte(
            coberturaLineas = 90.0,
            lineasCubiertas = 90,
            lineasPerdidas = 10,
            umbralCobertura = 80.0,
            testsEjecutados = 12,
            testsPasados = 12,
            testsFallados = 0,
            testsSalteados = 0,
            runId = 2002L,
        )
        metricaCIRepository.saveAndFlush(encontrada)

        val todas = metricaCIRepository.findByRepositorioIdOrderByFechaActualizacionDesc(repo.id!!)
        assertEquals(1, todas.size)
        assertEquals(90.0, todas.first().coberturaLineas)
        assertTrue(todas.first().cumpleUmbralCobertura)
        assertEquals(12, todas.first().testsPasados)
        assertEquals(0, todas.first().testsFallados)

        // Intentar insertar un duplicado directo con mismo repositorio y commit_sha viola el constraint único
        assertThrows(DataIntegrityViolationException::class.java) {
            metricaCIRepository.saveAndFlush(
                MetricaCI(
                    commitSha = "sha-unico-1",
                    coberturaLineas = 50.0,
                    repositorio = repo,
                )
            )
        }
    }

    @Test
    fun `findFirstByRepositorioIdOrderByFechaActualizacionDesc devuelve la metrica mas reciente`() {
        val repo = repositorioRepository.save(
            Repositorio(nombre = "repo-historial-ci", htmlUrl = "https://github.com/UNQlassroom/repo-historial-ci")
        )

        metricaCIRepository.saveAndFlush(
            MetricaCI(
                commitSha = "commit-viejo",
                coberturaLineas = 60.0,
                testsEjecutados = 5,
                testsPasados = 5,
                fechaActualizacion = LocalDateTime.now().minusHours(2),
                repositorio = repo,
            )
        )
        metricaCIRepository.saveAndFlush(
            MetricaCI(
                commitSha = "commit-nuevo",
                coberturaLineas = 88.0,
                cumpleUmbralCobertura = true,
                testsEjecutados = 15,
                testsPasados = 15,
                fechaActualizacion = LocalDateTime.now(),
                repositorio = repo,
            )
        )

        val ultima = metricaCIRepository.findFirstByRepositorioIdOrderByFechaActualizacionDesc(repo.id!!)
        assertNotNull(ultima)
        assertEquals("commit-nuevo", ultima?.commitSha)
        assertEquals(88.0, ultima?.coberturaLineas)
    }

    @Test
    fun `deleteAllByRepositorioIdIn elimina solo las metricas de los repositorios indicados`() {
        val repo1 = repositorioRepository.save(
            Repositorio(nombre = "repo-del-1", htmlUrl = "https://github.com/UNQlassroom/repo-del-1")
        )
        val repo2 = repositorioRepository.save(
            Repositorio(nombre = "repo-del-2", htmlUrl = "https://github.com/UNQlassroom/repo-del-2")
        )

        metricaCIRepository.saveAndFlush(MetricaCI(commitSha = "c1", coberturaLineas = 80.0, repositorio = repo1))
        metricaCIRepository.saveAndFlush(MetricaCI(commitSha = "c2", coberturaLineas = 90.0, repositorio = repo2))

        metricaCIRepository.deleteAllByRepositorioIdIn(listOf(repo1.id!!))

        assertTrue(metricaCIRepository.findByRepositorioIdOrderByFechaActualizacionDesc(repo1.id!!).isEmpty())
        assertEquals(1, metricaCIRepository.findByRepositorioIdOrderByFechaActualizacionDesc(repo2.id!!).size)
    }
}

