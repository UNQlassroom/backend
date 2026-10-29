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
class ReporteLinterRepositoryTest {

    @Autowired
    private lateinit var reporteLinterRepository: ReporteLinterRepository

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
    fun `persiste correctamente reporte e incidencias de linter asociados a alumno repositorio grupo y asignacion`() {
        val docente = usuarioRepository.save(Usuario(username = "docente_linter", esDocente = true))
        val alumno = usuarioRepository.save(Usuario(username = "alumno_linter", esDocente = false))
        val curso = cursoRepository.save(
            Curso(materia = "Ingeniería de Software", anio = 2026, semestre = 2, comision = 1, owner = docente)
        )
        val asignacion = asignacionRepository.save(
            Asignacion(
                titulo = "TP1 - Linter",
                tipo = TipoAsignacion.INDIVIDUAL,
                templateRepoName = "tmpl-tp1",
                curso = curso,
            )
        )
        val repo = repositorioRepository.save(
            Repositorio(
                nombre = "2026s2_c1_ing_tp1_alumno_linter",
                htmlUrl = "https://github.com/UNQlassroom/2026s2_c1_ing_tp1_alumno_linter",
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

        val nuevoReporte = ReporteLinter(
            commitSha = "sha-linter-001",
            herramienta = "ktlint",
            cantidadErrores = 1,
            cantidadAdvertencias = 2,
            totalIncidencias = 3,
            autorUsername = alumno.username,
            runId = 5500L,
            repositorio = repo,
            grupoAsignacion = grupo,
            asignacion = asignacion,
            alumno = alumno,
        )
        nuevoReporte.reemplazarIncidencias(
            listOf(
                IncidenciaLinter(
                    archivo = "src/main/kotlin/App.kt",
                    linea = 3,
                    columna = 1,
                    severidad = SeveridadLinter.ERROR,
                    regla = "no-unused-imports",
                    mensaje = "Unused import",
                ),
                IncidenciaLinter(
                    archivo = "src/main/kotlin/App.kt",
                    linea = 18,
                    columna = 12,
                    severidad = SeveridadLinter.WARNING,
                    regla = "MaxLineLength",
                    mensaje = "Line exceeds maximum length of 120 characters",
                ),
                IncidenciaLinter(
                    archivo = "src/main/kotlin/Service.kt",
                    linea = 42,
                    columna = null,
                    severidad = SeveridadLinter.WARNING,
                    regla = "MagicNumber",
                    mensaje = "This expression contains a magic number",
                ),
            )
        )

        val guardado = reporteLinterRepository.saveAndFlush(nuevoReporte)
        assertNotNull(guardado.id)

        val porRepo = reporteLinterRepository.findByRepositorioIdOrderByFechaActualizacionDesc(repo.id!!)
        val porGrupo = reporteLinterRepository.findByGrupoAsignacionIdOrderByFechaActualizacionDesc(grupo.id!!)
        val porAsignacion = reporteLinterRepository.findByAsignacionIdOrderByFechaActualizacionDesc(asignacion.id!!)
        val porAlumno = reporteLinterRepository.findByAlumnoIdOrderByFechaActualizacionDesc(alumno.id!!)

        assertEquals(1, porRepo.size)
        assertEquals(1, porGrupo.size)
        assertEquals(1, porAsignacion.size)
        assertEquals(1, porAlumno.size)

        val recuperado = porRepo.first()
        assertEquals("sha-linter-001", recuperado.commitSha)
        assertEquals("ktlint", recuperado.herramienta)
        assertEquals(1, recuperado.cantidadErrores)
        assertEquals(2, recuperado.cantidadAdvertencias)
        assertEquals(3, recuperado.totalIncidencias)
        assertEquals(3, recuperado.incidencias.size)
        assertEquals("src/main/kotlin/App.kt", recuperado.incidencias[0].archivo)
        assertEquals(3, recuperado.incidencias[0].linea)
        assertEquals(SeveridadLinter.ERROR, recuperado.incidencias[0].severidad)
        assertEquals("no-unused-imports", recuperado.incidencias[0].regla)
        assertEquals(alumno.id, recuperado.alumno?.id)
        assertEquals(asignacion.id, recuperado.asignacion?.id)
        assertEquals(grupo.id, recuperado.grupoAsignacion?.id)
    }

    @Test
    fun `asegura unicidad e idempotencia al actualizar un reporte existente del mismo commit`() {
        val repo = repositorioRepository.save(
            Repositorio(nombre = "repo-unicidad-linter", htmlUrl = "https://github.com/UNQlassroom/repo-unicidad-linter")
        )

        val inicial = ReporteLinter(
            commitSha = "sha-unico-linter",
            herramienta = "detekt",
            cantidadErrores = 2,
            cantidadAdvertencias = 1,
            totalIncidencias = 3,
            repositorio = repo,
        )
        inicial.reemplazarIncidencias(
            listOf(
                IncidenciaLinter(archivo = "src/A.kt", linea = 10, severidad = SeveridadLinter.ERROR, regla = "UnusedImport"),
                IncidenciaLinter(archivo = "src/A.kt", linea = 20, severidad = SeveridadLinter.ERROR, regla = "WildcardImport"),
                IncidenciaLinter(archivo = "src/B.kt", linea = 5, severidad = SeveridadLinter.WARNING, regla = "MagicNumber"),
            )
        )
        reporteLinterRepository.saveAndFlush(inicial)

        val encontrado = reporteLinterRepository.findByRepositorioIdAndCommitSha(repo.id!!, "sha-unico-linter")
        assertNotNull(encontrado)

        encontrado!!.actualizarDesdeReporte(
            cantidadErrores = 0,
            cantidadAdvertencias = 1,
            nuevasIncidencias = listOf(
                IncidenciaLinter(archivo = "src/B.kt", linea = 5, severidad = SeveridadLinter.WARNING, regla = "MagicNumber"),
            ),
            runId = 8888L,
        )
        reporteLinterRepository.saveAndFlush(encontrado)

        val todos = reporteLinterRepository.findByRepositorioIdOrderByFechaActualizacionDesc(repo.id!!)
        assertEquals(1, todos.size)
        assertEquals(0, todos.first().cantidadErrores)
        assertEquals(1, todos.first().cantidadAdvertencias)
        assertEquals(1, todos.first().totalIncidencias)
        assertEquals(1, todos.first().incidencias.size)
        assertEquals("MagicNumber", todos.first().incidencias.first().regla)

        assertThrows(DataIntegrityViolationException::class.java) {
            reporteLinterRepository.saveAndFlush(
                ReporteLinter(
                    commitSha = "sha-unico-linter",
                    repositorio = repo,
                )
            )
        }
    }

    @Test
    fun `findFirstByRepositorioIdOrderByFechaActualizacionDesc devuelve el reporte mas reciente`() {
        val repo = repositorioRepository.save(
            Repositorio(nombre = "repo-historial-linter", htmlUrl = "https://github.com/UNQlassroom/repo-historial-linter")
        )

        reporteLinterRepository.saveAndFlush(
            ReporteLinter(
                commitSha = "commit-viejo",
                cantidadErrores = 5,
                cantidadAdvertencias = 4,
                totalIncidencias = 9,
                fechaActualizacion = LocalDateTime.now().minusHours(3),
                repositorio = repo,
            )
        )
        reporteLinterRepository.saveAndFlush(
            ReporteLinter(
                commitSha = "commit-nuevo",
                cantidadErrores = 0,
                cantidadAdvertencias = 2,
                totalIncidencias = 2,
                fechaActualizacion = LocalDateTime.now(),
                repositorio = repo,
            )
        )

        val ultimo = reporteLinterRepository.findFirstByRepositorioIdOrderByFechaActualizacionDesc(repo.id!!)
        assertNotNull(ultimo)
        assertEquals("commit-nuevo", ultimo?.commitSha)
        assertEquals(0, ultimo?.cantidadErrores)
        assertEquals(2, ultimo?.cantidadAdvertencias)
    }

    @Test
    fun `deleteAllByRepositorioIdIn elimina los reportes de los repositorios indicados`() {
        val repo1 = repositorioRepository.save(
            Repositorio(nombre = "repo-linter-del-1", htmlUrl = "https://github.com/UNQlassroom/repo-linter-del-1")
        )
        val repo2 = repositorioRepository.save(
            Repositorio(nombre = "repo-linter-del-2", htmlUrl = "https://github.com/UNQlassroom/repo-linter-del-2")
        )

        val r1 = ReporteLinter(commitSha = "c1", cantidadErrores = 1, totalIncidencias = 1, repositorio = repo1)
        r1.reemplazarIncidencias(
            listOf(IncidenciaLinter(archivo = "src/A.kt", linea = 1, severidad = SeveridadLinter.ERROR, regla = "unused-import"))
        )
        reporteLinterRepository.saveAndFlush(r1)
        reporteLinterRepository.saveAndFlush(ReporteLinter(commitSha = "c2", cantidadAdvertencias = 2, totalIncidencias = 2, repositorio = repo2))

        reporteLinterRepository.deleteAllByRepositorioIdIn(listOf(repo1.id!!))

        assertTrue(reporteLinterRepository.findByRepositorioIdOrderByFechaActualizacionDesc(repo1.id!!).isEmpty())
        assertEquals(1, reporteLinterRepository.findByRepositorioIdOrderByFechaActualizacionDesc(repo2.id!!).size)
    }
}

