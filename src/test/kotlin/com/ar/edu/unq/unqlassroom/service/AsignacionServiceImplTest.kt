package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.*
import com.ar.edu.unq.unqlassroom.errors.BadRequestException
import com.ar.edu.unq.unqlassroom.errors.ForbiddenException
import com.ar.edu.unq.unqlassroom.github.GitHubCollaboratorService
import com.ar.edu.unq.unqlassroom.github.GitHubRepoResponse
import com.ar.edu.unq.unqlassroom.github.GitHubRepoService
import com.ar.edu.unq.unqlassroom.github.RepositorioInfo
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
import java.util.Optional

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

    @InjectMocks
    private lateinit var asignacionService: AsignacionServiceImpl

    private fun anyAsignacion(): Asignacion {
        any(Asignacion::class.java)
        return Asignacion(
            titulo = "",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "",
            curso = Curso(materia = "", anio = 0, semestre = 1, comision = 1)
        )
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

        val result = asignacionService.crearAsignacion(10L, request, "profe_test")

        assertNotNull(result)
        assertEquals(100L, result.id)
        assertEquals(10L, result.cursoId)
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

        val result = asignacionService.crearAsignacion(10L, request, "profe_test")

        assertNotNull(result)
        assertEquals(200L, result.id)
        assertEquals(TipoAsignacion.GRUPAL, result.tipo)
        assertEquals(1, result.grupos.size)
        val grupoDTO = result.grupos[0]
        assertEquals("Grupo Alpha", grupoDTO.nombre)
        assertEquals(listOf("alumno1", "alumno2"), grupoDTO.integrantes)
        assertEquals(repoName, grupoDTO.repositorio?.nombre)

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
        `when`(inscripcionRepository.findByCursoId(10L)).thenReturn(
            listOf(Inscripcion(curso = curso, usuario = alumno1), Inscripcion(curso = curso, usuario = alumno2))
        )

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
            asignacionService.crearAsignacion(10L, request, "profe_test")
        }
        assertEquals("Un alumno no puede pertenecer a más de un grupo en la misma asignación", ex.message)
    }

    @Test
    fun `crearAsignacion GRUPAL throws BadRequestException when a student is not enrolled in curso`() {
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
                CrearGrupoRequestDTO(nombre = "Grupo 1", integrantesUsernames = listOf("alumno1", "alumno_fantasma")),
            )
        )

        val ex = assertThrows<BadRequestException> {
            asignacionService.crearAsignacion(10L, request, "profe_test")
        }
        assertTrue(ex.message!!.contains("alumno_fantasma"))
    }

    @Test
    fun `crearAsignacion throws ForbiddenException when solicitante is not the owner docente`() {
        val docenteOwner = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docenteOwner)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))

        val request = CrearAsignacionRequestDTO(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "template-tp",
        )

        val ex = assertThrows<ForbiddenException> {
            asignacionService.crearAsignacion(10L, request, "otro_usuario")
        }
        assertEquals("Solo el docente a cargo del curso puede crear asignaciones", ex.message)
    }

    @Test
    fun `crearAsignacion throws BadRequestException when template repo does not exist in GitHub`() {
        val docenteOwner = Usuario(id = 1L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docenteOwner)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(gitHubRepoService.repositoryExists("template_inexistente")).thenReturn(false)

        val request = CrearAsignacionRequestDTO(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "template_inexistente",
        )

        val ex = assertThrows<BadRequestException> {
            asignacionService.crearAsignacion(10L, request, "profe_owner")
        }
        assertEquals("El repositorio template 'template_inexistente' no existe en GitHub", ex.message)
    }

    @Test
    fun `obtenerAsignaciones returns filtered groups when solicitante is enrolled alumno`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val alumno1 = Usuario(id = 2L, username = "alumno1")
        val alumno2 = Usuario(id = 3L, username = "alumno2")
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(10L, "alumno1")).thenReturn(
            Inscripcion(curso = curso, usuario = alumno1)
        )

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

        `when`(asignacionRepository.findByCursoId(10L)).thenReturn(listOf(asignacion))

        val result = asignacionService.obtenerAsignaciones(10L, "alumno1")

        assertEquals(1, result.size)
        // alumno1 only sees their own group
        assertEquals(1, result[0].grupos.size)
        assertEquals(listOf("alumno1"), result[0].grupos[0].integrantes)
    }

    @Test
    fun `obtenerAsignaciones returns all groups when solicitante is owner docente`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val alumno1 = Usuario(id = 2L, username = "alumno1")
        val alumno2 = Usuario(id = 3L, username = "alumno2")
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)

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

        `when`(asignacionRepository.findByCursoId(10L)).thenReturn(listOf(asignacion))

        val result = asignacionService.obtenerAsignaciones(10L, "profe_test")

        assertEquals(1, result.size)
        // owner sees all groups
        assertEquals(2, result[0].grupos.size)
    }

    @Test
    fun `obtenerAsignacion refreshes repo info and returns details`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        val curso = Curso(id = 10L, materia = "BD", anio = 2026, semestre = 1, comision = 1, owner = docente)

        `when`(cursoRepository.findById(10L)).thenReturn(Optional.of(curso))

        val repo = Repositorio(
            nombre = "repo1",
            htmlUrl = "https://github.com/repo1",
            ultimoCommit = "Old commit",
            estadoCI = "pending"
        )
        val asignacion = Asignacion(
            id = 50L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )
        val grupo = GrupoAsignacion(id = 101L, asignacion = asignacion, repositorio = repo, integrantes = mutableListOf())
        asignacion.grupos.add(grupo)

        `when`(asignacionRepository.findByIdAndCursoId(50L, 10L)).thenReturn(asignacion)

        val updatedInfo = RepositorioInfo(
            nombre = "repo1",
            htmlUrl = "https://github.com/repo1",
            ultimoCommit = "Fresh commit",
            fechaUltimoCommit = "2026-09-24T18:20:00Z",
            estadoCI = "success"
        )
        `when`(gitHubRepoService.obtenerInformacionRepositorio("repo1")).thenReturn(updatedInfo)

        val result = asignacionService.obtenerAsignacion(10L, 50L, "profe_test")

        assertEquals(50L, result.id)
        assertEquals("Fresh commit", result.grupos[0].repositorio?.ultimoCommit)
        assertEquals("success", result.grupos[0].repositorio?.estadoCI)
    }

    @Test
    fun `crearTemplateRepository delegates to gitHubRepoService and returns DTO`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        `when`(usuarioService.obtenerDocente("profe_test")).thenReturn(docente)

        val repoResponse = GitHubRepoResponse(
            name = "template-base-kotlin",
            fullName = "UNQlassroom/template-base-kotlin",
            htmlUrl = "https://github.com/UNQlassroom/template-base-kotlin",
            description = "Template base"
        )
        `when`(gitHubRepoService.createTemplateRepository("template-base-kotlin", "Template base")).thenReturn(repoResponse)

        val result = asignacionService.crearTemplateRepository(
            CrearTemplateRepoRequestDTO("template-base-kotlin", "Template base"),
            "profe_test"
        )

        assertEquals("template-base-kotlin", result.name)
        assertEquals("https://github.com/UNQlassroom/template-base-kotlin", result.htmlUrl)
    }

    @Test
    fun `listarTemplates returns template repositories`() {
        val docente = Usuario(id = 1L, username = "profe_test", esDocente = true)
        `when`(usuarioService.obtenerDocente("profe_test")).thenReturn(docente)

        val repo1 = GitHubRepoResponse(name = "tmpl1", fullName = "org/tmpl1", htmlUrl = "https://github.com/org/tmpl1", isTemplate = true)
        `when`(gitHubRepoService.listTemplateRepositories()).thenReturn(listOf(repo1))

        val result = asignacionService.listarTemplates("profe_test")

        assertEquals(1, result.size)
        assertEquals("tmpl1", result[0].name)
    }
}
