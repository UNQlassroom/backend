package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.errors.CursoNotFoundException
import com.ar.edu.unq.unqlassroom.errors.CursoSinGitHubRepoAsociadoException
import com.ar.edu.unq.unqlassroom.github.GitHubCollaboratorResponse
import com.ar.edu.unq.unqlassroom.github.GitHubCollaboratorService
import com.ar.edu.unq.unqlassroom.github.GitHubRepoMemberResponse
import com.ar.edu.unq.unqlassroom.github.GitHubRepoResponse
import com.ar.edu.unq.unqlassroom.github.GitHubRepoService
import com.ar.edu.unq.unqlassroom.github.RepositorioInfo
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Inscripcion
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.errors.ForbiddenException
import com.ar.edu.unq.unqlassroom.errors.UsuarioNotFoundException
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import com.ar.edu.unq.unqlassroom.service.impl.CursoServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class CursoServiceImplTest {

    @Mock
    private lateinit var cursoRepository: CursoRepository

    @Mock
    private lateinit var usuarioService: UsuarioService

    @Mock
    private lateinit var inscripcionRepository: InscripcionRepository

    @Mock
    private lateinit var gitHubRepoService: GitHubRepoService

    @Mock
    private lateinit var gitHubCollaboratorService: GitHubCollaboratorService

    @InjectMocks
    private lateinit var cursoService: CursoServiceImpl

    private fun anyString(): String {
        Mockito.anyString()
        return ""
    }

    private fun anyCurso(): Curso {
        Mockito.any(Curso::class.java)
        return Curso(materia = "", anio = 0, semestre = 1, comision = 1)
    }

    @Test
    fun `crearCurso creates org repository on github and saves curso with repo details`() {
        val requestDTO = CursoRequestDTO(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
        )

        val docente = Usuario(id = 10L, username = "profe_test", esDocente = true)
        `when`(usuarioService.obtenerDocente("profe_test")).thenReturn(docente)

        val repoResponse = GitHubRepoResponse(
            id = 123456L,
            name = "2026s1_c1_estructuras_de_datos",
            fullName = "UNQlassroom/2026s1_c1_estructuras_de_datos",
            htmlUrl = "https://github.com/UNQlassroom/2026s1_c1_estructuras_de_datos"
        )

        `when`(gitHubRepoService.createOrgRepository(
            name = anyString(),
            description = anyString(),
            private = Mockito.anyBoolean(),
            autoInit = Mockito.anyBoolean(),
            org = Mockito.isNull()
        )).thenReturn(repoResponse)

        `when`(cursoRepository.save(anyCurso())).thenAnswer { invocation ->
            val curso = invocation.getArgument<Curso>(0)
            Curso(
                id = 1L,
                materia = curso.materia,
                anio = curso.anio,
                semestre = curso.semestre,
                comision = curso.comision,
                descripcion = curso.descripcion,
                githubRepoId = curso.githubRepoId,
                githubRepoName = curso.githubRepoName,
            )
        }

        val result = cursoService.crearCurso(requestDTO, "profe_test")

        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals("Estructuras de Datos", result.materia)
        assertEquals(2026, result.anio)
        assertEquals(1, result.semestre)
        assertEquals(1, result.comision)
        assertEquals("Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1", result.descripcion)
        assertEquals(123456L, result.githubRepoId)
        assertEquals("2026s1_c1_estructuras_de_datos", result.githubRepoName)

        verify(gitHubRepoService).createOrgRepository(
            name = "2026s1_c1_estructuras_de_datos",
            description = "Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1",
            private = true,
            autoInit = true,
            org = null
        )
    }

    @Test
    fun `generarNombreRepo produces the expected snake_case format`() {
        val curso1 = Curso(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1
        )
        assertEquals("2026s1_c1_estructuras_de_datos", curso1.generarNombreRepo())

        val curso2 = Curso(
            materia = "Bases de Datos",
            anio = 2026,
            semestre = 2,
            comision = 2
        )
        assertEquals("2026s2_c2_bases_de_datos", curso2.generarNombreRepo())
    }

    @Test
    fun `generarDescripcionRepo produces the expected generic description`() {
        val curso = Curso(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1
        )
        assertEquals("Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1", curso.generarDescripcionRepo())
    }

    @Test
    fun `agregarAlumnos calls gitHubRepoService addCollaborator for each distinct username and saves alumno with repositorio`() {
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            githubRepoId = 123456L,
            githubRepoName = "2026s1_c1_estructuras_de_datos"
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val membership1 = GitHubCollaboratorResponse(
            username = "alumno1",
            role = "push",
            state = "active"
        )
        val membership2 = GitHubCollaboratorResponse(
            username = "alumno2",
            role = "push",
            state = "pending"
        )

        `when`(gitHubCollaboratorService.addCollaborator("2026s1_c1_estructuras_de_datos", "alumno1", "push", null))
            .thenReturn(membership1)
        `when`(gitHubCollaboratorService.addCollaborator("2026s1_c1_estructuras_de_datos_alumno1", "alumno1", "push", null))
            .thenReturn(membership1)
        `when`(gitHubCollaboratorService.addCollaborator("2026s1_c1_estructuras_de_datos", "alumno2", "push", null))
            .thenReturn(membership2)
        `when`(gitHubCollaboratorService.addCollaborator("2026s1_c1_estructuras_de_datos_alumno2", "alumno2", "push", null))
            .thenReturn(membership2)

        `when`(gitHubRepoService.generarNombreRepo(curso, "alumno1"))
            .thenReturn("2026s1_c1_estructuras_de_datos_alumno1")
        `when`(gitHubRepoService.generarNombreRepo(curso, "alumno2"))
            .thenReturn("2026s1_c1_estructuras_de_datos_alumno2")
        `when`(gitHubRepoService.generarDescripcionRepo(curso, "alumno1"))
            .thenReturn("desc alumno1")
        `when`(gitHubRepoService.generarDescripcionRepo(curso, "alumno2"))
            .thenReturn("desc alumno2")

        val repoInfo1 = RepositorioInfo(
            nombre = "2026s1_c1_estructuras_de_datos_alumno1",
            htmlUrl = "https://github.com/UNQlassroom/2026s1_c1_estructuras_de_datos_alumno1",
            ultimoCommit = "Initial commit",
            fechaUltimoCommit = "2026-09-09T18:00:00Z",
            estadoCI = "sin_ci"
        )
        val repoInfo2 = RepositorioInfo(
            nombre = "2026s1_c1_estructuras_de_datos_alumno2",
            htmlUrl = "https://github.com/UNQlassroom/2026s1_c1_estructuras_de_datos_alumno2",
            ultimoCommit = "Initial commit",
            fechaUltimoCommit = "2026-09-09T18:00:00Z",
            estadoCI = "sin_ci"
        )

        `when`(gitHubRepoService.obtenerInformacionRepositorio("2026s1_c1_estructuras_de_datos_alumno1"))
            .thenReturn(repoInfo1)
        `when`(gitHubRepoService.obtenerInformacionRepositorio("2026s1_c1_estructuras_de_datos_alumno2"))
            .thenReturn(repoInfo2)

        `when`(usuarioService.obtenerOCrearAlumno(Mockito.anyString())).thenAnswer { invocation ->
            val u = invocation.getArgument<String>(0)
            Usuario(id = 1L, username = u, esDocente = false)
        }
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(Mockito.anyLong(), Mockito.anyString())).thenReturn(null)
        `when`(inscripcionRepository.save(Mockito.any(Inscripcion::class.java))).thenAnswer { it.getArgument(0) }

        val request = AgregarAlumnosRequestDTO(
            usernames = listOf("alumno1", "alumno2", "alumno1 ")
        )

        val response = cursoService.agregarAlumnos(1L, request)

        assertEquals(1L, response.cursoId)
        assertEquals("2026s1_c1_estructuras_de_datos", response.repoName)
        assertEquals(2, response.alumnos.size)
        assertEquals("alumno1", response.alumnos[0].username)
        assertEquals("active", response.alumnos[0].state)
        assertEquals("push", response.alumnos[0].role)
        assertNotNull(response.alumnos[0].repositorio)
        assertEquals("2026s1_c1_estructuras_de_datos_alumno1", response.alumnos[0].repositorio?.nombre)
        assertEquals("Initial commit", response.alumnos[0].repositorio?.ultimoCommit)

        assertEquals("alumno2", response.alumnos[1].username)
        assertEquals("pending", response.alumnos[1].state)
        assertEquals("push", response.alumnos[1].role)
        assertNotNull(response.alumnos[1].repositorio)

        verify(gitHubCollaboratorService).addCollaborator("2026s1_c1_estructuras_de_datos", "alumno1", "push", null)
        verify(gitHubCollaboratorService).addCollaborator("2026s1_c1_estructuras_de_datos", "alumno2", "push", null)
        verify(gitHubRepoService).createOrgRepository(
            name = "2026s1_c1_estructuras_de_datos_alumno1",
            description = "desc alumno1",
            private = true,
            autoInit = true,
        )
        verify(gitHubCollaboratorService).addCollaborator(
            repoName = "2026s1_c1_estructuras_de_datos_alumno1",
            username = "alumno1",
            permission = "push",
        )
        verify(gitHubRepoService).createOrgRepository(
            name = "2026s1_c1_estructuras_de_datos_alumno2",
            description = "desc alumno2",
            private = true,
            autoInit = true,
        )
        verify(gitHubCollaboratorService).addCollaborator(
            repoName = "2026s1_c1_estructuras_de_datos_alumno2",
            username = "alumno2",
            permission = "push",
        )
        verify(inscripcionRepository, Mockito.times(2)).save(Mockito.any(Inscripcion::class.java))
    }

    @Test
    fun `agregarAlumnos no genera repo si el alumno ya tiene un repo para el curso actual pero guarda y retorna repositorio`() {
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            githubRepoId = 123456L,
            githubRepoName = "2026s1_c1_estructuras_de_datos"
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val membership1 = GitHubCollaboratorResponse(
            username = "alumno1",
            role = "push",
            state = "active"
        )

        `when`(gitHubCollaboratorService.addCollaborator("2026s1_c1_estructuras_de_datos", "alumno1", "push", null))
            .thenReturn(membership1)
        `when`(gitHubRepoService.generarNombreRepo(curso, "alumno1"))
            .thenReturn("2026s1_c1_estructuras_de_datos_alumno1")
        `when`(gitHubRepoService.repositoryExists("2026s1_c1_estructuras_de_datos_alumno1"))
            .thenReturn(true)

        val repoInfo = RepositorioInfo(
            nombre = "2026s1_c1_estructuras_de_datos_alumno1",
            htmlUrl = "https://github.com/UNQlassroom/2026s1_c1_estructuras_de_datos_alumno1",
            ultimoCommit = "Segundo commit",
            fechaUltimoCommit = "2026-09-09T19:00:00Z",
            estadoCI = "success"
        )
        `when`(gitHubRepoService.obtenerInformacionRepositorio("2026s1_c1_estructuras_de_datos_alumno1"))
            .thenReturn(repoInfo)

        `when`(usuarioService.obtenerOCrearAlumno("alumno1")).thenReturn(Usuario(id = 1L, username = "alumno1", esDocente = false))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(1L, "alumno1")).thenReturn(null)
        `when`(inscripcionRepository.save(Mockito.any(Inscripcion::class.java))).thenAnswer { it.getArgument(0) }

        val request = AgregarAlumnosRequestDTO(usernames = listOf("alumno1"))
        val response = cursoService.agregarAlumnos(1L, request)

        assertEquals(1, response.alumnos.size)
        assertEquals("alumno1", response.alumnos[0].username)
        assertNotNull(response.alumnos[0].repositorio)
        assertEquals("Segundo commit", response.alumnos[0].repositorio?.ultimoCommit)
        assertEquals("success", response.alumnos[0].repositorio?.estadoCI)

        verify(gitHubCollaboratorService).addCollaborator("2026s1_c1_estructuras_de_datos", "alumno1", "push", null)
        verify(gitHubRepoService, Mockito.never()).createOrgRepository(
            name = Mockito.anyString(),
            description = Mockito.any(),
            private = Mockito.anyBoolean(),
            autoInit = Mockito.anyBoolean(),
            org = Mockito.any()
        )
        verify(inscripcionRepository).save(Mockito.any(Inscripcion::class.java))
    }

    @Test
    fun `agregarAlumnos throws CursoNotFoundException when curso does not exist`() {
        `when`(cursoRepository.findById(99L)).thenReturn(Optional.empty())

        val exception = assertThrows<CursoNotFoundException> {
            cursoService.agregarAlumnos(99L, AgregarAlumnosRequestDTO(listOf("alumno1")))
        }

        assertEquals("Curso no encontrado", exception.message)
    }

    @Test
    fun `agregarAlumnos throws 400 BAD_REQUEST when curso has no github repo name`() {
        val curso = Curso(
            id = 2L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            githubRepoName = null
        )
        `when`(cursoRepository.findById(2L)).thenReturn(Optional.of(curso))

        val exception = assertThrows<CursoSinGitHubRepoAsociadoException> {
            cursoService.agregarAlumnos(2L, AgregarAlumnosRequestDTO(listOf("alumno1")))
        }

        assertEquals("Curso sin repositorio de GitHub asociado", exception.message)
    }

    @Test
    fun `obtenerAlumnos returns members from GitHub and correlates with DB entity`() {
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            githubRepoId = 123456L,
            githubRepoName = "2026s1_c1_estructuras_de_datos"
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val repoMembers = listOf(
            GitHubRepoMemberResponse(username = "alumno1", role = "write", state = "active"),
            GitHubRepoMemberResponse(username = "alumno2", role = "push", state = "pending")
        )
        `when`(gitHubCollaboratorService.getRepoMembers("2026s1_c1_estructuras_de_datos")).thenReturn(repoMembers)

        val usuario1 = Usuario(id = 1L, username = "alumno1", esDocente = false)
        val inscripcionPersistida1 = Inscripcion(
            usuario = usuario1,
            githubRole = "write",
            githubState = "active",
            curso = curso,
            repositorio = Repositorio(
                nombre = "2026s1_c1_estructuras_de_datos_tp1_alumno1",
                htmlUrl = "https://github.com/UNQlassroom/2026s1_c1_estructuras_de_datos_tp1_alumno1",
                ultimoCommit = "Fix tests",
                fechaUltimoCommit = "2026-09-09T20:00:00Z",
                estadoCI = "success"
            )
        )
        `when`(inscripcionRepository.findByCursoId(1L)).thenReturn(listOf(inscripcionPersistida1))

        val updatedRepoInfo = RepositorioInfo(
            nombre = "2026s1_c1_estructuras_de_datos_tp1_alumno1",
            htmlUrl = "https://github.com/UNQlassroom/2026s1_c1_estructuras_de_datos_tp1_alumno1",
            ultimoCommit = "Nuevo commit",
            fechaUltimoCommit = "2026-09-10T10:00:00Z",
            estadoCI = "failure"
        )
        `when`(gitHubRepoService.obtenerInformacionRepositorio("2026s1_c1_estructuras_de_datos_tp1_alumno1"))
            .thenReturn(updatedRepoInfo)

        val response = cursoService.obtenerAlumnos(1L)

        assertEquals(1L, response.cursoId)
        assertEquals("2026s1_c1_estructuras_de_datos", response.repoName)
        assertEquals(2, response.alumnos.size)

        val a1 = response.alumnos.first { it.username == "alumno1" }
        assertNotNull(a1.repositorio)
        assertEquals("Nuevo commit", a1.repositorio?.ultimoCommit)
        assertEquals("2026-09-10T10:00:00Z", a1.repositorio?.fechaUltimoCommit)
        assertEquals("failure", a1.repositorio?.estadoCI)
        assertEquals("Nuevo commit", inscripcionPersistida1.repositorio?.ultimoCommit)

        val a2 = response.alumnos.first { it.username == "alumno2" }
        assertNull(a2.repositorio)
    }

    @Test
    fun `crearCurso remueve tildes de materia al crear repo en github y guardar curso`() {
        val requestDTO = CursoRequestDTO(
            materia = "Programación Funcional",
            anio = 2026,
            semestre = 2,
            comision = 3,
        )

        val docente = Usuario(id = 10L, username = "profe_test", esDocente = true)
        `when`(usuarioService.obtenerDocente("profe_test")).thenReturn(docente)

        val repoResponse = GitHubRepoResponse(
            id = 777L,
            name = "2026s2_c3_programacion_funcional",
            fullName = "UNQlassroom/2026s2_c3_programacion_funcional",
            htmlUrl = "https://github.com/UNQlassroom/2026s2_c3_programacion_funcional"
        )

        `when`(gitHubRepoService.createOrgRepository(
            name = anyString(),
            description = anyString(),
            private = Mockito.anyBoolean(),
            autoInit = Mockito.anyBoolean(),
            org = Mockito.isNull()
        )).thenReturn(repoResponse)

        `when`(cursoRepository.save(anyCurso())).thenAnswer { invocation ->
            val curso = invocation.getArgument<Curso>(0)
            Curso(
                id = 5L,
                materia = curso.materia,
                anio = curso.anio,
                semestre = curso.semestre,
                comision = curso.comision,
                descripcion = curso.descripcion,
                githubRepoId = curso.githubRepoId,
                githubRepoName = curso.githubRepoName,
            )
        }

        val result = cursoService.crearCurso(requestDTO, "profe_test")

        assertNotNull(result)
        assertEquals("Programación Funcional", result.materia)
        assertEquals("2026s2_c3_programacion_funcional", result.githubRepoName)

        verify(gitHubRepoService).createOrgRepository(
            name = "2026s2_c3_programacion_funcional",
            description = "Curso de Programación Funcional - Año 2026 - Semestre 2 - Comisión 3",
            private = true,
            autoInit = true,
            org = null
        )
    }

    @Test
    fun `crearCurso assigns owner from authenticated user`() {
        val requestDTO = CursoRequestDTO(
            materia = "Sistemas Distribuidos",
            anio = 2026,
            semestre = 1,
            comision = 1,
        )

        val repoResponse = GitHubRepoResponse(
            id = 888L,
            name = "2026s1_c1_sistemas_distribuidos",
            fullName = "UNQlassroom/2026s1_c1_sistemas_distribuidos",
            htmlUrl = "https://github.com/UNQlassroom/2026s1_c1_sistemas_distribuidos"
        )

        `when`(gitHubRepoService.createOrgRepository(
            name = anyString(),
            description = anyString(),
            private = Mockito.anyBoolean(),
            autoInit = Mockito.anyBoolean(),
            org = Mockito.isNull()
        )).thenReturn(repoResponse)

        val docente = Usuario(id = 99L, username = "profe_juan", esDocente = true)
        `when`(usuarioService.obtenerDocente("profe_juan")).thenReturn(docente)

        `when`(cursoRepository.save(anyCurso())).thenAnswer { invocation ->
            val c = invocation.getArgument<Curso>(0)
            Curso(
                id = 15L,
                materia = c.materia,
                anio = c.anio,
                semestre = c.semestre,
                comision = c.comision,
                descripcion = c.descripcion,
                githubRepoId = c.githubRepoId,
                githubRepoName = c.githubRepoName,
                owner = c.owner,
            )
        }

        val result = cursoService.crearCurso(requestDTO, "profe_juan")

        assertNotNull(result)
        assertEquals("profe_juan", result.ownerUsername)
    }

    @Test
    fun `crearCurso propagates ForbiddenException when owner is not docente`() {
        val requestDTO = CursoRequestDTO(
            materia = "Redes",
            anio = 2026,
            semestre = 1,
            comision = 1,
        )
        `when`(usuarioService.obtenerDocente("alumno_infiltrado"))
            .thenThrow(ForbiddenException("El usuario alumno_infiltrado no tiene permisos de docente"))

        val ex = assertThrows<ForbiddenException> {
            cursoService.crearCurso(requestDTO, "alumno_infiltrado")
        }
        assertEquals("El usuario alumno_infiltrado no tiene permisos de docente", ex.message)
    }

    @Test
    fun `crearCurso propagates UsuarioNotFoundException when owner does not exist`() {
        val requestDTO = CursoRequestDTO(
            materia = "Redes",
            anio = 2026,
            semestre = 1,
            comision = 1,
        )
        `when`(usuarioService.obtenerDocente("fantasma"))
            .thenThrow(UsuarioNotFoundException("Usuario no encontrado: fantasma"))

        val ex = assertThrows<UsuarioNotFoundException> {
            cursoService.crearCurso(requestDTO, "fantasma")
        }
        assertEquals("Usuario no encontrado: fantasma", ex.message)
    }

    @Test
    fun `Inscripcion defaults githubRole to push and githubState to pending`() {
        val curso = Curso(materia = "BD", anio = 2026, semestre = 1, comision = 1)
        val usuario = Usuario(username = "alumno1")
        val inscripcion = Inscripcion(curso = curso, usuario = usuario)

        assertEquals("push", inscripcion.githubRole)
        assertEquals("pending", inscripcion.githubState)
    }
}
