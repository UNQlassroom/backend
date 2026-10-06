package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.dto.asignacion.request.*
import com.ar.edu.unq.unqlassroom.dto.asignacion.response.*
import com.ar.edu.unq.unqlassroom.exception.AsignacionNotFoundException
import com.ar.edu.unq.unqlassroom.exception.BadRequestException
import com.ar.edu.unq.unqlassroom.exception.CursoNotFoundException
import com.ar.edu.unq.unqlassroom.exception.ForbiddenException
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubCollaboratorService
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubIssueItemResponse
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubIssueService
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubIssueUser
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoResponse
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.integration.github.service.RepositorioInfo
import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.AsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.service.impl.AsignacionServiceImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDateTime
import java.util.Optional
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubReleaseResponse

@ExtendWith(MockitoExtension::class)
class AsignacionServiceImplTest {

    @Mock
    private lateinit var asignacionRepository: AsignacionRepository

    @Mock
    private lateinit var cursoRepository: CursoRepository

    @Mock
    private lateinit var inscripcionRepository: InscripcionRepository

    @Mock
    private lateinit var usuarioService: UsuarioService

    @Mock
    private lateinit var gitHubRepoService: GitHubRepoService

    @Mock
    private lateinit var gitHubCollaboratorService: GitHubCollaboratorService

    @Mock
    private lateinit var gitHubIssueService: GitHubIssueService

    @InjectMocks
    private lateinit var asignacionService: AsignacionServiceImpl

    private fun anyAsignacion(): Asignacion {
        any(Asignacion::class.java)
        return Asignacion(
            titulo = "TP Dummy",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = Curso(materia = "BD", anio = 2026, semestre = 1, comision = 1)
        )
    }

    private fun anyString(): String {
        any(String::class.java)
        return ""
    }

    private fun eqString(value: String): String {
        eq(value)
        return ""
    }

    private fun <T> anyNullable(clazz: Class<T>): T? {
        nullable(clazz)
        return null
    }

    @Test
    fun `generarNombreRepo on Asignacion produces expected name with year, semester, comision, materia, titulo and suffix`() {
        val curso = Curso(
            materia = "Programación Concurrente",
            anio = 2026,
            semestre = 2,
            comision = 3
        )
        val asignacion = Asignacion(
            titulo = "TP 1 - Procesos & Hilos",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso
        )

        assertEquals(
            "2026s2_c3_programacion_concurrente_tp_1_procesos_hilos_alumno_juan",
            asignacion.generarNombreRepo("alumno_juan")
        )
        assertEquals(
            "2026s2_c3_programacion_concurrente_tp_1_procesos_hilos_grupo_omega",
            asignacion.generarNombreRepo("Grupo Omega")
        )
    }

    @Test
    fun `crearAsignacion INDIVIDUAL generates repos from template including curso name, titulo and username`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 10L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = docente,
        )
        val alumno1 = Usuario(id = 2L, username = "alumno1")
        val alumno2 = Usuario(id = 3L, username = "alumno2")
        val inscripcion1 = Inscripcion(curso = curso, usuario = alumno1)
        val inscripcion2 = Inscripcion(curso = curso, usuario = alumno2)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(gitHubRepoService.repositoryExists("template-tp1")).thenReturn(true)
        `when`(inscripcionRepository.findByCursoId(10L)).thenReturn(listOf(inscripcion1, inscripcion2))

        val repoName1 = "2026s1_c1_estructuras_de_datos_tp1_alumno1"
        val repoName2 = "2026s1_c1_estructuras_de_datos_tp1_alumno2"

        `when`(gitHubRepoService.repositoryExists(repoName1)).thenReturn(false)
        `when`(gitHubRepoService.repositoryExists(repoName2)).thenReturn(false)

        val repoInfo1 = RepositorioInfo(
            nombre = repoName1,
            htmlUrl = "https://github.com/UNQlassroom/$repoName1",
            ultimoCommit = "Initial commit",
            fechaUltimoCommit = "2026-09-24T18:00:00Z",
            estadoCI = "success"
        )
        val repoInfo2 = RepositorioInfo(
            nombre = repoName2,
            htmlUrl = "https://github.com/UNQlassroom/$repoName2",
            ultimoCommit = "Initial commit",
            fechaUltimoCommit = "2026-09-24T18:00:00Z",
            estadoCI = "pending"
        )
        `when`(gitHubRepoService.obtenerInformacionRepositorio(repoName1)).thenReturn(repoInfo1)
        `when`(gitHubRepoService.obtenerInformacionRepositorio(repoName2)).thenReturn(repoInfo2)

        `when`(asignacionRepository.save(anyAsignacion())).thenAnswer { invocation ->
            val asig = invocation.getArgument<Asignacion>(0)
            Asignacion(
                id = 100L,
                titulo = asig.titulo,
                descripcion = asig.descripcion,
                tipo = asig.tipo,
                templateRepoName = asig.templateRepoName,
                fechaLimite = asig.fechaLimite,
                curso = asig.curso,
                grupos = asig.grupos,
            )
        }

        val request = CrearAsignacionRequestDTO(
            titulo = "TP1",
            descripcion = "Primer trabajo individual",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "template-tp1",
        )

        val result = asignacionService.crearAsignacion(10L, request.aModelo(), "profe_test")

        assertNotNull(result)
        assertEquals(100L, result.id)
        assertEquals(10L, result.curso?.id)
        assertEquals("TP1", result.titulo)
        assertEquals(TipoAsignacion.INDIVIDUAL, result.tipo)
        assertEquals(2, result.grupos.size)

        verify(gitHubRepoService).createRepositoryFromTemplate(
            templateRepoName = "template-tp1",
            newRepoName = repoName1,
            description = "Repositorio de asignación 'TP1' (alumno1) - Estructuras de Datos (2026s1 comision 1)",
            private = true,
        )
        verify(gitHubCollaboratorService).addCollaborator(
            repoName = repoName1,
            username = "alumno1",
            permission = "push",
        )
        verify(gitHubCollaboratorService).addCollaborator(
            repoName = repoName1,
            username = "profe_test",
            permission = "push",
        )
        verify(gitHubRepoService).createRepositoryFromTemplate(
            templateRepoName = "template-tp1",
            newRepoName = repoName2,
            description = "Repositorio de asignación 'TP1' (alumno2) - Estructuras de Datos (2026s1 comision 1)",
            private = true,
        )
        verify(gitHubCollaboratorService).addCollaborator(
            repoName = repoName2,
            username = "alumno2",
            permission = "push",
        )
        verify(gitHubCollaboratorService).addCollaborator(
            repoName = repoName2,
            username = "profe_test",
            permission = "push",
        )
    }

    @Test
    fun `crearAsignacion GRUPAL creates shared repo and adds all group members as collaborators`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 10L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = docente,
        )
        val alumno1 = Usuario(id = 2L, username = "alumno1")
        val alumno2 = Usuario(id = 3L, username = "alumno2")
        val inscripcion1 = Inscripcion(curso = curso, usuario = alumno1)
        val inscripcion2 = Inscripcion(curso = curso, usuario = alumno2)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(gitHubRepoService.repositoryExists("template-tp2")).thenReturn(true)
        `when`(inscripcionRepository.findByCursoId(10L)).thenReturn(listOf(inscripcion1, inscripcion2))

        val repoName = "2026s1_c1_estructuras_de_datos_tp2_grupo_alpha"
        `when`(gitHubRepoService.repositoryExists(repoName)).thenReturn(false)

        `when`(usuarioService.obtenerOCrearAlumno("alumno1")).thenReturn(alumno1)
        `when`(usuarioService.obtenerOCrearAlumno("alumno2")).thenReturn(alumno2)

        val repoInfo = RepositorioInfo(
            nombre = repoName,
            htmlUrl = "https://github.com/UNQlassroom/$repoName",
            ultimoCommit = "Initial commit",
            fechaUltimoCommit = "2026-09-24T18:00:00Z",
            estadoCI = "sin_ci"
        )
        `when`(gitHubRepoService.obtenerInformacionRepositorio(repoName)).thenReturn(repoInfo)

        `when`(asignacionRepository.save(anyAsignacion())).thenAnswer { invocation ->
            val asig = invocation.getArgument<Asignacion>(0)
            Asignacion(
                id = 200L,
                titulo = asig.titulo,
                descripcion = asig.descripcion,
                tipo = asig.tipo,
                templateRepoName = asig.templateRepoName,
                fechaLimite = asig.fechaLimite,
                curso = asig.curso,
                grupos = asig.grupos,
            )
        }

        val request = CrearAsignacionRequestDTO(
            titulo = "TP2",
            descripcion = "Trabajo grupal",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "template-tp2",
            grupos = listOf(
                CrearGrupoRequestDTO(
                    nombre = "Grupo Alpha",
                    integrantesUsernames = listOf("alumno1", "alumno2")
                )
            )
        )

        val result = asignacionService.crearAsignacion(10L, request.aModelo(), "profe_test")

        assertNotNull(result)
        assertEquals(200L, result.id)
        assertEquals(TipoAsignacion.GRUPAL, result.tipo)
        assertEquals(1, result.grupos.size)
        val grupo = result.grupos[0]
        assertEquals("Grupo Alpha", grupo.nombre)
        assertEquals(listOf("alumno1", "alumno2"), grupo.integrantes.map { it.username })
        assertEquals(repoName, grupo.repositorio?.nombre)

        verify(gitHubRepoService).createRepositoryFromTemplate(
            templateRepoName = "template-tp2",
            newRepoName = repoName,
            description = "Repositorio de asignación 'TP2' (Grupo Alpha) - Estructuras de Datos (2026s1 comision 1)",
            private = true,
        )
        verify(gitHubCollaboratorService).addCollaborator(repoName, "alumno1", "push")
        verify(gitHubCollaboratorService).addCollaborator(repoName, "alumno2", "push")
        verify(gitHubCollaboratorService).addCollaborator(repoName, "profe_test", "push")
    }

    @Test
    fun `crearAsignacion GRUPAL throws BadRequestException when a student is repeated across groups`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)
        val alumno1 = Usuario(id = 2L, username = "alumno1")
        val alumno2 = Usuario(id = 3L, username = "alumno2")

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(gitHubRepoService.repositoryExists("template-tp")).thenReturn(true)

        val request = CrearAsignacionRequestDTO(
            titulo = "TP Grupal",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "template-tp",
            grupos = listOf(
                CrearGrupoRequestDTO(nombre = "Grupo 1", integrantesUsernames = listOf("alumno1")),
                CrearGrupoRequestDTO(nombre = "Grupo 2", integrantesUsernames = listOf("alumno1", "alumno2")),
            )
        )

        val ex = assertThrows<BadRequestException> {
            asignacionService.crearAsignacion(10L, request.aModelo(), "profe_test")
        }
        assertEquals("Un alumno no puede pertenecer a más de un grupo en la misma asignación", ex.message)
    }

    @Test
    fun `crearAsignacion GRUPAL throws BadRequestException when a student is not enrolled`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)
        val alumno1 = Usuario(id = 2L, username = "alumno1")

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(gitHubRepoService.repositoryExists("template-tp")).thenReturn(true)
        `when`(inscripcionRepository.findByCursoId(10L)).thenReturn(
            listOf(Inscripcion(curso = curso, usuario = alumno1))
        )

        val request = CrearAsignacionRequestDTO(
            titulo = "TP Grupal",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "template-tp",
            grupos = listOf(
                CrearGrupoRequestDTO(nombre = "Grupo 1", integrantesUsernames = listOf("alumno_no_inscripto")),
            )
        )

        val ex = assertThrows<BadRequestException> {
            asignacionService.crearAsignacion(10L, request.aModelo(), "profe_test")
        }
        assertEquals("Los siguientes alumnos no están inscriptos en el curso: alumno_no_inscripto", ex.message)
    }

    @Test
    fun `crearAsignacion throws BadRequestException when template repo does not exist`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(gitHubRepoService.repositoryExists("template-inexistente")).thenReturn(false)

        val request = CrearAsignacionRequestDTO(
            titulo = "TP Invalido",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "template-inexistente",
        )

        val ex = assertThrows<BadRequestException> {
            asignacionService.crearAsignacion(10L, request.aModelo(), "profe_test")
        }
        assertEquals("El repositorio template 'template-inexistente' no existe en GitHub", ex.message)
    }

    @Test
    fun `crearAsignacion throws ForbiddenException when user is not the teacher`() {
        val docenteOwner = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docenteOwner)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))

        val request = CrearAsignacionRequestDTO(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
        )

        assertThrows<ForbiddenException> {
            asignacionService.crearAsignacion(10L, request.aModelo(), "alumno_hacker")
        }
    }

    @Test
    fun `crearAsignacion throws CursoNotFoundException when course does not exist`() {
        `when`(cursoRepository.findById(99L)).thenReturn(Optional.empty())

        val request = CrearAsignacionRequestDTO(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
        )

        assertThrows<CursoNotFoundException> {
            asignacionService.crearAsignacion(99L, request.aModelo(), "profe")
        }
    }

    @Test
    fun `obtenerAsignaciones returns all groups when requested by teacher`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)
        val alumno1 = Usuario(id = 2L, username = "alumno1")
        val alumno2 = Usuario(id = 3L, username = "alumno2")

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))

        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )
        val grupo1 = GrupoAsignacion(
            id = 1L,
            asignacion = asignacion,
            repositorio = Repositorio(nombre = "r1", htmlUrl = "http://r1"),
            integrantes = mutableListOf(alumno1),
        )
        val grupo2 = GrupoAsignacion(
            id = 2L,
            asignacion = asignacion,
            repositorio = Repositorio(nombre = "r2", htmlUrl = "http://r2"),
            integrantes = mutableListOf(alumno2),
        )
        asignacion.grupos.addAll(listOf(grupo1, grupo2))

        `when`(asignacionRepository.findByCursoId(10L)).thenReturn(listOf(asignacion))

        val result = asignacionService.obtenerAsignaciones(10L, "profe_owner")

        assertEquals(1, result.size)
        assertEquals(2, result[0].grupos.size)
    }

    @Test
    fun `obtenerAsignaciones filters groups to only student's own group`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)
        val alumno1 = Usuario(id = 2L, username = "alumno1")
        val alumno2 = Usuario(id = 3L, username = "alumno2")

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "alumno1")).thenReturn(
            Inscripcion(curso = curso, usuario = alumno1)
        )

        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )
        val grupo1 = GrupoAsignacion(
            id = 1L,
            asignacion = asignacion,
            repositorio = Repositorio(nombre = "r1", htmlUrl = "http://r1"),
            integrantes = mutableListOf(alumno1),
        )
        val grupo2 = GrupoAsignacion(
            id = 2L,
            asignacion = asignacion,
            repositorio = Repositorio(nombre = "r2", htmlUrl = "http://r2"),
            integrantes = mutableListOf(alumno2),
        )
        asignacion.grupos.addAll(listOf(grupo1, grupo2))

        `when`(asignacionRepository.findByCursoId(10L)).thenReturn(listOf(asignacion))

        val result = asignacionService.obtenerAsignaciones(10L, "alumno1")

        assertEquals(1, result.size)
        assertEquals(1, result[0].grupos.size)
        assertEquals(listOf("alumno1"), result[0].grupos[0].integrantes.map { it.username })
    }

    @Test
    fun `obtenerAsignacion throws ForbiddenException when student is not enrolled and not owner`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "intruso")).thenReturn(null)

        assertThrows<ForbiddenException> {
            asignacionService.obtenerAsignacion(10L, 50L, "intruso")
        }
    }

    @Test
    fun `obtenerAsignacion returns updated CI and commit info for groups`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)
        val alumno = Usuario(id = 2L, username = "alumno1")

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))

        val repo = Repositorio(
            nombre = "repo1",
            htmlUrl = "http://r1",
            ultimoCommit = "Old commit",
            fechaUltimoCommit = "2026-09-01T00:00:00Z",
            estadoCI = "sin_ci"
        )
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )
        val grupo = GrupoAsignacion(id = 1L, asignacion = asignacion, repositorio = repo, integrantes = mutableListOf(alumno))
        asignacion.grupos.add(grupo)

        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(gitHubRepoService.obtenerInformacionRepositorio("repo1")).thenReturn(
            RepositorioInfo(
                nombre = "repo1",
                htmlUrl = "http://r1",
                ultimoCommit = "Fresh commit",
                fechaUltimoCommit = "2026-09-25T12:00:00Z",
                estadoCI = "success"
            )
        )

        val result = asignacionService.obtenerAsignacion(10L, 50L, "profe_owner")

        val repoResult = result.grupos[0].repositorio
        assertEquals("Fresh commit", repoResult?.ultimoCommit)
        assertEquals("2026-09-25T12:00:00Z", repoResult?.fechaUltimoCommit)
        assertEquals("success", repoResult?.estadoCI)
    }

    @Test
    fun `calificarAsignacion assigns nota and feedback to specified group and persists`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)
        val alumno = Usuario(id = 2L, username = "alumno1")

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))

        val repo = Repositorio(nombre = "repo1", htmlUrl = "https://github.com/repo1")
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )
        val grupo = GrupoAsignacion(id = 101L, asignacion = asignacion, repositorio = repo, integrantes = mutableListOf(alumno))
        asignacion.grupos.add(grupo)

        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(asignacionRepository.save(asignacion)).thenReturn(asignacion)

        val response = asignacionService.calificarAsignacion(10L, 50L, 101L, 10, "Excelente trabajo individual", "profe_owner")

        assertEquals("Excelente trabajo individual", grupo.observaciones)
        assertNotNull(grupo.fechaCalificacion)
        assertEquals(10, grupo.calificacion)
        assertEquals("Excelente trabajo individual", grupo.observaciones)
        verify(asignacionRepository).save(asignacion)
    }

    @Test
    fun `calificarAsignacion updates calificacion and observaciones`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)
        val alumno = Usuario(id = 2L, username = "alumno1")

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))

        val repo = Repositorio(nombre = "repo1", htmlUrl = "https://github.com/repo1")
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )
        val grupo = GrupoAsignacion(id = 101L, asignacion = asignacion, repositorio = repo, integrantes = mutableListOf(alumno))
        asignacion.grupos.add(grupo)

        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(asignacionRepository.save(asignacion)).thenReturn(asignacion)

        asignacionService.calificarAsignacion(10L, 50L, 101L, 8, "Buen enfoque", "profe_owner")
        assertEquals(8, grupo.calificacion)
        assertEquals("Buen enfoque", grupo.observaciones)

        asignacionService.calificarAsignacion(10L, 50L, 101L, 7, "Aprobado con observaciones", "profe_owner")
        assertEquals(7, grupo.calificacion)
        assertEquals("Aprobado con observaciones", grupo.observaciones)
    }

    @Test
    fun `calificarAsignacion updates the specified group when multiple groups exist`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)
        val alumno1 = Usuario(id = 2L, username = "alumno1")
        val alumno2 = Usuario(id = 3L, username = "alumno2")

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))

        val repo1 = Repositorio(nombre = "repo1", htmlUrl = "https://github.com/repo1")
        val repo2 = Repositorio(nombre = "repo2", htmlUrl = "https://github.com/repo2")
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )
        val grupo1 = GrupoAsignacion(id = 101L, asignacion = asignacion, repositorio = repo1, integrantes = mutableListOf(alumno1))
        val grupo2 = GrupoAsignacion(id = 102L, asignacion = asignacion, repositorio = repo2, integrantes = mutableListOf(alumno2))
        asignacion.grupos.addAll(listOf(grupo1, grupo2))

        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(asignacionRepository.save(asignacion)).thenReturn(asignacion)

        asignacionService.calificarAsignacion(10L, 50L, 102L, 9, "Muy buen trabajo", "profe_owner")

        assertNull(grupo1.calificacion)
        assertEquals(9, grupo2.calificacion)
        assertEquals("Muy buen trabajo", grupo2.observaciones)
    }

    @Test
    fun `calificarAsignacion throws BadRequestException when nota is outside 1 to 10`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )
        val grupo = GrupoAsignacion(id = 101L, asignacion = asignacion)
        asignacion.grupos.add(grupo)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)

        val ex1 = assertThrows<BadRequestException> {
            asignacionService.calificarAsignacion(10L, 50L, 101L, 0, null, "profe_owner")
        }
        assertEquals("La nota debe ser entre 1 y 10", ex1.message)

        val ex2 = assertThrows<BadRequestException> {
            asignacionService.calificarAsignacion(10L, 50L, 101L, 11, null, "profe_owner")
        }
        assertEquals("La nota debe ser entre 1 y 10", ex2.message)
    }

    @Test
    fun `calificarAsignacion throws ForbiddenException when solicitante is not course owner docente`() {
        val docenteOwner = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docenteOwner)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))

        val ex = assertThrows<ForbiddenException> {
            asignacionService.calificarAsignacion(10L, 50L, 101L, 8, null, "otro_docente")
        }
        assertEquals("Solo el docente a cargo del curso puede calificar asignaciones", ex.message)
    }

    @Test
    fun `calificarAsignacion throws CursoNotFoundException when curso does not exist`() {
        `when`(cursoRepository.findById(99L)).thenReturn(Optional.empty())

        assertThrows<CursoNotFoundException> {
            asignacionService.calificarAsignacion(99L, 50L, 101L, 8, null, "profe")
        }
    }

    @Test
    fun `calificarAsignacion throws AsignacionNotFoundException when asignacion does not exist`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(99L, 10L)).thenReturn(null)

        assertThrows<AsignacionNotFoundException> {
            asignacionService.calificarAsignacion(10L, 99L, 101L, 8, null, "profe_owner")
        }
    }

    @Test
    fun `calificarAsignacion throws BadRequestException when grupo does not belong to asignacion`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)

        val ex = assertThrows<BadRequestException> {
            asignacionService.calificarAsignacion(10L, 50L, 999L, 8, null, "profe_owner")
        }
        assertEquals("El grupo especificado no pertenece a la asignación", ex.message)
    }

    @Test
    fun `obtenerCorrecciones calculates ACTUALIZADO when commit is after issue createdAt`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val alumno = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val curso = Curso(id = 10L, materia = "Objetos", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "template-tp1",
            curso = curso
        )
        val repo = Repositorio(
            id = 100L,
            nombre = "repo-tp1-alumno1",
            htmlUrl = "https://github.com/UNQlassroom/repo-tp1-alumno1",
            ultimoCommit = "fix(test): correccion",
            fechaUltimoCommit = "2026-09-30T15:00:00Z" // 3 PM
        )
        val grupo = GrupoAsignacion(
            id = 200L,
            asignacion = asignacion,
            integrantes = mutableListOf(alumno),
            repositorio = repo
        )
        asignacion.grupos.add(grupo)

        val issue = GitHubIssueItemResponse(
            number = 1,
            title = "Corregir test 2",
            state = "open",
            htmlUrl = "https://github.com/UNQlassroom/repo-tp1-alumno1/issues/1",
            user = GitHubIssueUser(login = "profe_owner"),
            comments = 0,
            createdAt = "2026-09-30T10:00:00Z", // 10 AM
            updatedAt = "2026-09-30T10:00:00Z",
            closedAt = null
        )

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(gitHubIssueService.getRepositoryIssues("repo-tp1-alumno1")).thenReturn(listOf(issue))

        val resultado = asignacionService.obtenerCorrecciones(10L, 50L, "profe_owner")

        assertEquals(1, resultado.size)
        val grupoResultado = resultado[0]
        assertEquals("repo-tp1-alumno1", grupoResultado.repoNombre)
        assertEquals(1, grupoResultado.issues.size)
        val issueResultado = grupoResultado.issues[0]
        assertEquals("ACTUALIZADO", issueResultado.estado)
        assertTrue(issueResultado.tieneCommitsPosteriores)
    }

    @Test
    fun `obtenerCorrecciones calculates PENDIENTE when no commit after and no comments`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val alumno = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val curso = Curso(id = 10L, materia = "Objetos", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "template-tp1",
            curso = curso
        )
        val repo = Repositorio(
            id = 100L,
            nombre = "repo-tp1-alumno1",
            htmlUrl = "https://github.com/UNQlassroom/repo-tp1-alumno1",
            ultimoCommit = "init commit",
            fechaUltimoCommit = "2026-09-29T15:00:00Z" // Día anterior
        )
        val grupo = GrupoAsignacion(
            id = 200L,
            asignacion = asignacion,
            integrantes = mutableListOf(alumno),
            repositorio = repo
        )
        asignacion.grupos.add(grupo)

        val issue = GitHubIssueItemResponse(
            number = 1,
            title = "Corregir test 2",
            state = "open",
            htmlUrl = "https://github.com/UNQlassroom/repo-tp1-alumno1/issues/1",
            user = GitHubIssueUser(login = "profe_owner"),
            comments = 0,
            createdAt = "2026-09-30T10:00:00Z",
            updatedAt = "2026-09-30T10:00:00Z",
            closedAt = null
        )

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(gitHubIssueService.getRepositoryIssues("repo-tp1-alumno1")).thenReturn(listOf(issue))

        val resultado = asignacionService.obtenerCorrecciones(10L, 50L, "profe_owner")

        assertEquals(1, resultado.size)
        val issueResultado = resultado[0].issues[0]
        assertEquals("PENDIENTE", issueResultado.estado)
        assertFalse(issueResultado.tieneCommitsPosteriores)
    }

    @Test
    fun `obtenerCorrecciones calculates RESUELTO when issue is closed`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val alumno = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val curso = Curso(id = 10L, materia = "Objetos", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "template-tp1",
            curso = curso
        )
        val repo = Repositorio(
            id = 100L,
            nombre = "repo-tp1-alumno1",
            htmlUrl = "https://github.com/UNQlassroom/repo-tp1-alumno1",
            ultimoCommit = "ultimo commit",
            fechaUltimoCommit = "2026-09-30T15:00:00Z"
        )
        val grupo = GrupoAsignacion(
            id = 200L,
            asignacion = asignacion,
            integrantes = mutableListOf(alumno),
            repositorio = repo
        )
        asignacion.grupos.add(grupo)

        val issue = GitHubIssueItemResponse(
            number = 1,
            title = "Corregir test 2",
            state = "closed",
            htmlUrl = "https://github.com/UNQlassroom/repo-tp1-alumno1/issues/1",
            user = GitHubIssueUser(login = "profe_owner"),
            comments = 2,
            createdAt = "2026-09-30T10:00:00Z",
            updatedAt = "2026-09-30T16:00:00Z",
            closedAt = "2026-09-30T16:00:00Z"
        )

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(gitHubIssueService.getRepositoryIssues("repo-tp1-alumno1")).thenReturn(listOf(issue))

        val resultado = asignacionService.obtenerCorrecciones(10L, 50L, "profe_owner")

        assertEquals(1, resultado.size)
        val issueResultado = resultado[0].issues[0]
        assertEquals("RESUELTO", issueResultado.estado)
    }

    @Test
    fun `obtenerCorrecciones throws ForbiddenException when user has no permissions`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "Objetos", anio = 2026, semestre = 2, comision = 1, owner = docente)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "extranio")).thenReturn(null)

        val ex = assertThrows<ForbiddenException> {
            asignacionService.obtenerCorrecciones(10L, 50L, "extranio")
        }
        assertEquals("No tiene permisos para ver las correcciones de esta asignación", ex.message)
    }

    @Test
    fun `marcarAsignacionComoEntregada throws CursoNotFoundException when curso not found`() {
        `when`(cursoRepository.findById(999L)).thenReturn(Optional.empty())

        assertThrows<CursoNotFoundException> {
            asignacionService.marcarAsignacionComoEntregada(999L, 1L, "profe", null)
        }
    }

    @Test
    fun `marcarAsignacionComoEntregada throws AsignacionNotFoundException when asignacion not found`() {
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1)
        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(null)

        assertThrows<AsignacionNotFoundException> {
            asignacionService.marcarAsignacionComoEntregada(10L, 50L, "profe", null)
        }
    }

    @Test
    fun `marcarAsignacionComoEntregada throws BadRequestException when deadline expired`() {
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1)
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            fechaLimite = LocalDateTime.now().minusDays(1),
            curso = curso
        )
        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)

        val ex = assertThrows<BadRequestException> {
            asignacionService.marcarAsignacionComoEntregada(10L, 50L, "alumno", null)
        }
        assertEquals("No se puede entregar la asignación porque la fecha límite ha vencido", ex.message)
    }

    @Test
    fun `marcarAsignacionComoEntregada throws ForbiddenException when user is not owner and not enrolled`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            fechaLimite = LocalDateTime.now().plusDays(1),
            curso = curso
        )
        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "intruso")).thenReturn(null)

        val ex = assertThrows<ForbiddenException> {
            asignacionService.marcarAsignacionComoEntregada(10L, 50L, "intruso", null)
        }
        assertEquals("No tiene permisos para entregar esta asignación", ex.message)
    }

    @Test
    fun `marcarAsignacionComoEntregada as owner throws BadRequestException when grupoId is null`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            fechaLimite = LocalDateTime.now().plusDays(1),
            curso = curso
        )
        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)

        val ex = assertThrows<BadRequestException> {
            asignacionService.marcarAsignacionComoEntregada(10L, 50L, "profe_owner", null)
        }
        assertEquals("Debe especificar el grupoId para marcar la entrega como docente", ex.message)
    }

    @Test
    fun `marcarAsignacionComoEntregada as owner throws BadRequestException when grupoId not in asignacion`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            fechaLimite = LocalDateTime.now().plusDays(1),
            curso = curso
        )
        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)

        val ex = assertThrows<BadRequestException> {
            asignacionService.marcarAsignacionComoEntregada(10L, 50L, "profe_owner", 999L)
        }
        assertEquals("El grupo especificado no pertenece a la asignación", ex.message)
    }

    @Test
    fun `marcarAsignacionComoEntregada as student throws BadRequestException when student in no group`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            fechaLimite = LocalDateTime.now().plusDays(1),
            curso = curso
        )
        val alumno = Usuario(id = 2L, username = "alumno_sin_grupo", esDocente = false)
        val inscripcion = Inscripcion(id = 100L, curso = curso, usuario = alumno)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "alumno_sin_grupo")).thenReturn(inscripcion)

        val ex = assertThrows<BadRequestException> {
            asignacionService.marcarAsignacionComoEntregada(10L, 50L, "alumno_sin_grupo", null)
        }
        assertEquals("El usuario no pertenece a ningún grupo de esta asignación", ex.message)
    }

    @Test
    fun `marcarAsignacionComoEntregada as student throws ForbiddenException when specifying another grupoId`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val alumno = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val repo = Repositorio(id = 1L, nombre = "repo1", htmlUrl = "https://github.com/repo1")
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            fechaLimite = LocalDateTime.now().plusDays(1),
            curso = curso
        )
        val grupo = GrupoAsignacion(id = 101L, asignacion = asignacion, repositorio = repo, integrantes = mutableListOf(alumno))
        asignacion.grupos.add(grupo)

        val inscripcion = Inscripcion(id = 100L, curso = curso, usuario = alumno)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "alumno1")).thenReturn(inscripcion)

        val ex = assertThrows<ForbiddenException> {
            asignacionService.marcarAsignacionComoEntregada(10L, 50L, "alumno1", 999L)
        }
        assertEquals("No tiene permisos para entregar en nombre de otro grupo", ex.message)
    }

    @Test
    fun `marcarAsignacionComoEntregada by student successfully delivers and creates release`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val alumno = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val repo = Repositorio(id = 1L, nombre = "repo1", htmlUrl = "https://github.com/repo1")
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            fechaLimite = LocalDateTime.now().plusDays(1),
            curso = curso
        )
        val grupo = GrupoAsignacion(id = 101L, asignacion = asignacion, repositorio = repo, integrantes = mutableListOf(alumno))
        asignacion.grupos.add(grupo)

        val inscripcion = Inscripcion(id = 100L, curso = curso, usuario = alumno)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "alumno1")).thenReturn(inscripcion)

        val release = GitHubReleaseResponse(id = 12L, tagName = "entrega-v1", htmlUrl = "https://release.url/v1")
        `when`(gitHubRepoService.createRelease(
            repoName = eqString("repo1"),
            tagName = eqString("entrega-v1"),
            name = anyString(),
            body = anyString(),
            targetCommitish = anyNullable(String::class.java),
            org = anyNullable(String::class.java)
        )).thenReturn(release)

        val repoInfo = RepositorioInfo(
            nombre = "repo1",
            htmlUrl = "https://github.com/repo1",
            ultimoCommit = "Commit entrega",
            fechaUltimoCommit = "2026-10-02T18:00:00Z",
            estadoCI = "success"
        )
        `when`(gitHubRepoService.obtenerInformacionRepositorio("repo1")).thenReturn(repoInfo)
        `when`(asignacionRepository.save(anyAsignacion())).thenReturn(asignacion)

        val res = asignacionService.marcarAsignacionComoEntregada(10L, 50L, "alumno1", 101L)

        assertTrue(grupo.entregada)
        assertEquals(1, grupo.cantidadEntregas)
        assertEquals("https://release.url/v1", grupo.releaseUrl)
        assertNotNull(grupo.fechaEntregada)
        assertEquals("success", grupo.repositorio?.estadoCI)
        assertEquals(1, res.grupos.size)
    }

    @Test
    fun `marcarAsignacionComoEntregada succeeds even when createRelease and repoInfo throw exceptions`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val alumno = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val repo = Repositorio(id = 1L, nombre = "repo1", htmlUrl = "https://github.com/repo1")
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            fechaLimite = LocalDateTime.now().plusDays(1),
            curso = curso
        )
        val grupo = GrupoAsignacion(id = 101L, asignacion = asignacion, repositorio = repo, integrantes = mutableListOf(alumno))
        asignacion.grupos.add(grupo)

        val inscripcion = Inscripcion(id = 100L, curso = curso, usuario = alumno)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "alumno1")).thenReturn(inscripcion)

        `when`(gitHubRepoService.createRelease(
            repoName = anyString(),
            tagName = anyString(),
            name = anyString(),
            body = anyString(),
            targetCommitish = anyNullable(String::class.java),
            org = anyNullable(String::class.java)
        )).thenThrow(RuntimeException("Release API unavailable"))

        `when`(gitHubRepoService.obtenerInformacionRepositorio("repo1")).thenThrow(RuntimeException("Repo API unavailable"))
        `when`(asignacionRepository.save(anyAsignacion())).thenReturn(asignacion)

        val res = asignacionService.marcarAsignacionComoEntregada(10L, 50L, "alumno1", null)

        assertTrue(grupo.entregada)
        assertEquals(1, grupo.cantidadEntregas)
        assertNull(grupo.releaseUrl)
        assertNotNull(grupo.fechaEntregada)
        assertEquals(1, res.grupos.size)
    }

    @Test
    fun `marcarAsignacionComoEntregada by owner returns all groups`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val alumno1 = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val alumno2 = Usuario(id = 3L, username = "alumno2", esDocente = false)
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val repo1 = Repositorio(id = 1L, nombre = "repo1", htmlUrl = "https://github.com/repo1")
        val repo2 = Repositorio(id = 2L, nombre = "repo2", htmlUrl = "https://github.com/repo2")
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            fechaLimite = LocalDateTime.now().plusDays(1),
            curso = curso
        )
        val grupo1 = GrupoAsignacion(id = 101L, asignacion = asignacion, repositorio = repo1, integrantes = mutableListOf(alumno1))
        val grupo2 = GrupoAsignacion(id = 102L, asignacion = asignacion, repositorio = repo2, integrantes = mutableListOf(alumno2))
        asignacion.grupos.addAll(listOf(grupo1, grupo2))

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(gitHubRepoService.obtenerInformacionRepositorio("repo1")).thenReturn(RepositorioInfo(nombre = "repo1", htmlUrl = "https://github.com/repo1"))
        `when`(asignacionRepository.save(anyAsignacion())).thenReturn(asignacion)

        val res = asignacionService.marcarAsignacionComoEntregada(10L, 50L, "profe_owner", 101L)

        assertEquals(2, res.grupos.size)
    }

    @Test
    fun `obtenerAsignacion throws CursoNotFoundException and AsignacionNotFoundException`() {
        `when`(cursoRepository.findById(999L)).thenReturn(Optional.empty())
        assertThrows<CursoNotFoundException> {
            asignacionService.obtenerAsignacion(999L, 50L, "profe")
        }

        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = Usuario(username = "profe"))
        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(999L, 10L)).thenReturn(null)

        assertThrows<AsignacionNotFoundException> {
            asignacionService.obtenerAsignacion(10L, 999L, "profe")
        }
    }

    @Test
    fun `obtenerAsignacion preserves existing repo data when gitHubRepoService fails`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val alumno = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val repo = Repositorio(id = 1L, nombre = "repo1", htmlUrl = "https://github.com/repo1", ultimoCommit = "prev commit", estadoCI = "pending")
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso
        )
        val grupo = GrupoAsignacion(id = 101L, asignacion = asignacion, repositorio = repo, integrantes = mutableListOf(alumno))
        asignacion.grupos.add(grupo)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(gitHubRepoService.obtenerInformacionRepositorio("repo1")).thenThrow(RuntimeException("Network error"))

        val res = asignacionService.obtenerAsignacion(10L, 50L, "profe_owner")

        assertEquals(1, res.grupos.size)
        assertEquals("prev commit", res.grupos[0].repositorio?.ultimoCommit)
        assertEquals("pending", res.grupos[0].repositorio?.estadoCI)
    }

    @Test
    fun `obtenerCorrecciones throws CursoNotFoundException and AsignacionNotFoundException`() {
        `when`(cursoRepository.findById(999L)).thenReturn(Optional.empty())
        assertThrows<CursoNotFoundException> {
            asignacionService.obtenerCorrecciones(999L, 50L, "profe")
        }

        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = Usuario(username = "profe"))
        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(999L, 10L)).thenReturn(null)

        assertThrows<AsignacionNotFoundException> {
            asignacionService.obtenerCorrecciones(10L, 999L, "profe")
        }
    }

    @Test
    fun `obtenerCorrecciones by student only returns student group corrections`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val alumno1 = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val alumno2 = Usuario(id = 3L, username = "alumno2", esDocente = false)
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val repo1 = Repositorio(id = 1L, nombre = "repo1", htmlUrl = "https://github.com/repo1")
        val repo2 = Repositorio(id = 2L, nombre = "repo2", htmlUrl = "https://github.com/repo2")
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso
        )
        val grupo1 = GrupoAsignacion(id = 101L, asignacion = asignacion, repositorio = repo1, integrantes = mutableListOf(alumno1))
        val grupo2 = GrupoAsignacion(id = 102L, asignacion = asignacion, repositorio = repo2, integrantes = mutableListOf(alumno2))
        asignacion.grupos.addAll(listOf(grupo1, grupo2))

        val inscripcion = Inscripcion(id = 100L, curso = curso, usuario = alumno1)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "alumno1")).thenReturn(inscripcion)
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(gitHubRepoService.obtenerInformacionRepositorio("repo1")).thenThrow(RuntimeException("Repo error"))
        `when`(gitHubIssueService.getRepositoryIssues("repo1")).thenReturn(emptyList())

        val res = asignacionService.obtenerCorrecciones(10L, 50L, "alumno1")

        assertEquals(1, res.size)
        assertEquals("repo1", res[0].repoNombre)
    }

    @Test
    fun `obtenerCorrecciones calculates ACTUALIZADO when issue has comments even if commit is before`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val alumno = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val repo = Repositorio(
            id = 1L,
            nombre = "repo1",
            htmlUrl = "https://github.com/repo1",
            fechaUltimoCommit = "2026-09-01T10:00:00Z"
        )
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso
        )
        val grupo = GrupoAsignacion(id = 101L, asignacion = asignacion, repositorio = repo, integrantes = mutableListOf(alumno))
        asignacion.grupos.add(grupo)

        val issueWithComments = GitHubIssueItemResponse(
            number = 1,
            title = "Feedback",
            htmlUrl = "https://github.com/issue/1",
            state = "open",
            comments = 3,
            createdAt = "2026-09-15T10:00:00Z"
        )

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(gitHubIssueService.getRepositoryIssues("repo1")).thenReturn(listOf(issueWithComments))

        val res = asignacionService.obtenerCorrecciones(10L, 50L, "profe_owner")

        assertEquals(1, res.size)
        assertEquals("ACTUALIZADO", res[0].issues[0].estado)
        assertFalse(res[0].issues[0].tieneCommitsPosteriores)
    }

    @Test
    fun `obtenerCorrecciones calculates PENDIENTE when commit date is null or invalid format`() {
        val docente = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val alumno = Usuario(id = 2L, username = "alumno1", esDocente = false)
        val curso = Curso(id = 10L, materia = "SO", anio = 2026, semestre = 2, comision = 1, owner = docente)
        val repo = Repositorio(
            id = 1L,
            nombre = "repo1",
            htmlUrl = "https://github.com/repo1",
            fechaUltimoCommit = "invalid-date-format"
        )
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso
        )
        val grupo = GrupoAsignacion(id = 101L, asignacion = asignacion, repositorio = repo, integrantes = mutableListOf(alumno))
        asignacion.grupos.add(grupo)

        val issueNoComments = GitHubIssueItemResponse(
            number = 1,
            title = "Feedback",
            htmlUrl = "https://github.com/issue/1",
            state = "open",
            comments = 0,
            createdAt = "2026-09-15T10:00:00Z"
        )

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)
        `when`(gitHubIssueService.getRepositoryIssues("repo1")).thenReturn(listOf(issueNoComments))

        val res = asignacionService.obtenerCorrecciones(10L, 50L, "profe_owner")

        assertEquals(1, res.size)
        assertEquals("PENDIENTE", res[0].issues[0].estado)
        assertFalse(res[0].issues[0].tieneCommitsPosteriores)
    }

    @Test
    fun `crearAsignacion throws BadRequestException when asignacion with same titulo already exists in curso`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        val existing = Asignacion(id = 1L, titulo = "TP1", tipo = TipoAsignacion.INDIVIDUAL, templateRepoName = "tmpl", curso = curso)
        `when`(asignacionRepository.findByCursoIdAndTituloIgnoreCase(10L, "TP1")).thenReturn(existing)

        val asignacion = Asignacion(titulo = "TP1", tipo = TipoAsignacion.INDIVIDUAL, templateRepoName = "tmpl")

        val ex = assertThrows<BadRequestException> {
            asignacionService.crearAsignacion(10L, asignacion, "profe_test")
        }
        assertEquals("Ya existe una asignación con el título 'TP1' en este curso", ex.message)
    }

    @Test
    fun `crearAsignacion throws BadRequestException when fechaLimite is in the past`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByCursoIdAndTituloIgnoreCase(10L, "TP1")).thenReturn(null)

        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            fechaLimite = LocalDateTime.now().minusDays(1)
        )

        val ex = assertThrows<BadRequestException> {
            asignacionService.crearAsignacion(10L, asignacion, "profe_test")
        }
        assertEquals("La fecha límite no puede ser anterior a la fecha actual", ex.message)
    }

    @Test
    fun `crearAsignacion INDIVIDUAL throws BadRequestException when grupos are specified`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByCursoIdAndTituloIgnoreCase(10L, "TP1")).thenReturn(null)
        `when`(gitHubRepoService.repositoryExists("tmpl")).thenReturn(true)

        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl"
        )
        val grupo = GrupoAsignacion(nombre = "Grupo 1", integrantes = mutableListOf(Usuario(username = "u1")))
        asignacion.grupos.add(grupo)

        val ex = assertThrows<BadRequestException> {
            asignacionService.crearAsignacion(10L, asignacion, "profe_test")
        }
        assertEquals("No se pueden especificar grupos para una asignación individual", ex.message)
    }

    @Test
    fun `crearAsignacion GRUPAL throws BadRequestException when a group has no members`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByCursoIdAndTituloIgnoreCase(10L, "TP1")).thenReturn(null)
        `when`(gitHubRepoService.repositoryExists("tmpl")).thenReturn(true)

        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl"
        )
        val grupo = GrupoAsignacion(nombre = "Grupo Vacio", integrantes = mutableListOf())
        asignacion.grupos.add(grupo)

        val ex = assertThrows<BadRequestException> {
            asignacionService.crearAsignacion(10L, asignacion, "profe_test")
        }
        assertEquals("Todos los grupos deben tener al menos un integrante", ex.message)
    }

    @Test
    fun `crearAsignacion GRUPAL throws BadRequestException when duplicate group names exist`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(asignacionRepository.findByCursoIdAndTituloIgnoreCase(10L, "TP1")).thenReturn(null)
        `when`(gitHubRepoService.repositoryExists("tmpl")).thenReturn(true)

        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl"
        )
        val u1 = Usuario(username = "u1")
        val u2 = Usuario(username = "u2")
        asignacion.grupos.add(GrupoAsignacion(nombre = "Grupo Alpha", integrantes = mutableListOf(u1)))
        asignacion.grupos.add(GrupoAsignacion(nombre = "grupo alpha", integrantes = mutableListOf(u2)))

        val ex = assertThrows<BadRequestException> {
            asignacionService.crearAsignacion(10L, asignacion, "profe_test")
        }
        assertEquals("No puede haber grupos con el mismo nombre en la misma asignación", ex.message)
    }

    @Test
    fun `generarNombreRepo throws IllegalStateException when asignacion has no curso`() {
        val asignacion = Asignacion(
            titulo = "TP 1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl"
        )
        val ex = assertThrows<IllegalStateException> {
            asignacion.generarNombreRepo("alumno")
        }
        assertEquals("La asignación no está asociada a ningún curso", ex.message)
    }
}

