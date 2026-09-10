package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.errors.CursoSinGitHubTeamAsociadoException
import com.ar.edu.unq.unqlassroom.github.GitHubRepoService
import com.ar.edu.unq.unqlassroom.github.GitHubTeamMemberResponse
import com.ar.edu.unq.unqlassroom.github.GitHubTeamMembershipResponse
import com.ar.edu.unq.unqlassroom.github.GitHubTeamResponse
import com.ar.edu.unq.unqlassroom.github.GitHubTeamService
import com.ar.edu.unq.unqlassroom.github.RepositorioInfo
import com.ar.edu.unq.unqlassroom.model.Alumno
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.repository.AlumnoRepository
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
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
    private lateinit var alumnoRepository: AlumnoRepository

    @Mock
    private lateinit var gitHubTeamService: GitHubTeamService

    @Mock
    private lateinit var gitHubRepoService: GitHubRepoService

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
    fun `crearCurso creates team on github and saves curso with team details`() {
        val requestDTO = CursoRequestDTO(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
        )

        val teamResponse = GitHubTeamResponse(
            id = 123456L,
            nodeId = "MDQ6VGVhbTEyMzQ1Ng==",
            name = "2026s1_c1_estructuras_de_datos",
            slug = "2026s1_c1_estructuras_de_datos",
            description = "Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1"
        )

        `when`(gitHubTeamService.createTeam(
            name = anyString(),
            description = anyString(),
            privacy = anyString(),
            org = Mockito.isNull()
        )).thenReturn(teamResponse)

        `when`(cursoRepository.save(anyCurso())).thenAnswer { invocation ->
            val curso = invocation.getArgument<Curso>(0)
            Curso(
                id = 1L,
                materia = curso.materia,
                anio = curso.anio,
                semestre = curso.semestre,
                comision = curso.comision,
                descripcion = curso.descripcion,
                githubTeamId = curso.githubTeamId,
                githubTeamSlug = curso.githubTeamSlug
            )
        }

        val result = cursoService.crearCurso(requestDTO)

        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals("Estructuras de Datos", result.materia)
        assertEquals(2026, result.anio)
        assertEquals(1, result.semestre)
        assertEquals(1, result.comision)
        assertEquals("Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1", result.descripcion)
        assertEquals(123456L, result.githubTeamId)
        assertEquals("2026s1_c1_estructuras_de_datos", result.githubTeamSlug)

        verify(gitHubTeamService).createTeam(
            name = "2026s1_c1_estructuras_de_datos",
            description = "Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1",
            privacy = "closed",
            org = null
        )
    }

    @Test
    fun `generarNombreTeam produces the expected snake_case format`() {
        val curso1 = Curso(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1
        )
        assertEquals("2026s1_c1_estructuras_de_datos", curso1.generarNombreTeam())

        val curso2 = Curso(
            materia = "Bases de Datos",
            anio = 2026,
            semestre = 2,
            comision = 2
        )
        assertEquals("2026s2_c2_bases_de_datos", curso2.generarNombreTeam())
    }

    @Test
    fun `generarDescripcionTeam produces the expected generic description`() {
        val curso = Curso(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1
        )
        assertEquals("Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1", curso.generarDescripcionTeam())
    }

    @Test
    fun `agregarAlumnos calls gitHubTeamService for each distinct username and returns response with repositorio`() {
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            githubTeamId = 123456L,
            githubTeamSlug = "2026s1_c1_estructuras_de_datos"
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val membership1 = GitHubTeamMembershipResponse(
            url = "url/alumno1",
            role = "member",
            state = "active"
        )
        val membership2 = GitHubTeamMembershipResponse(
            url = "url/alumno2",
            role = "member",
            state = "pending"
        )

        `when`(gitHubTeamService.addMemberToTeam("2026s1_c1_estructuras_de_datos", "alumno1", "member", null))
            .thenReturn(membership1)
        `when`(gitHubTeamService.addMemberToTeam("2026s1_c1_estructuras_de_datos", "alumno2", "member", null))
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

        `when`(alumnoRepository.findByCursoIdAndUsername(Mockito.anyLong(), Mockito.anyString())).thenReturn(null)
        `when`(alumnoRepository.save(Mockito.any(Alumno::class.java))).thenAnswer { it.getArgument(0) }

        val request = AgregarAlumnosRequestDTO(
            usernames = listOf("alumno1", "alumno2", "alumno1 ")
        )

        val response = cursoService.agregarAlumnos(1L, request)

        assertEquals(1L, response.cursoId)
        assertEquals("2026s1_c1_estructuras_de_datos", response.teamSlug)
        assertEquals(2, response.alumnos.size)
        assertEquals("alumno1", response.alumnos[0].username)
        assertEquals("active", response.alumnos[0].state)
        assertNotNull(response.alumnos[0].repositorio)
        assertEquals("2026s1_c1_estructuras_de_datos_alumno1", response.alumnos[0].repositorio?.nombre)
        assertEquals("Initial commit", response.alumnos[0].repositorio?.ultimoCommit)
        assertEquals("2026-09-09T18:00:00Z", response.alumnos[0].repositorio?.fechaUltimoCommit)
        assertEquals("sin_ci", response.alumnos[0].repositorio?.estadoCI)

        assertEquals("alumno2", response.alumnos[1].username)
        assertEquals("pending", response.alumnos[1].state)
        assertNotNull(response.alumnos[1].repositorio)

        verify(gitHubTeamService).addMemberToTeam("2026s1_c1_estructuras_de_datos", "alumno1", "member", null)
        verify(gitHubTeamService).addMemberToTeam("2026s1_c1_estructuras_de_datos", "alumno2", "member", null)
        verify(gitHubRepoService).createOrgRepository(
            name = "2026s1_c1_estructuras_de_datos_alumno1",
            description = "desc alumno1",
            private = true,
            autoInit = true,
        )
        verify(gitHubRepoService).addCollaborator(
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
        verify(gitHubRepoService).addCollaborator(
            repoName = "2026s1_c1_estructuras_de_datos_alumno2",
            username = "alumno2",
            permission = "push",
        )
        verify(alumnoRepository, Mockito.times(2)).save(Mockito.any(Alumno::class.java))
    }

    @Test
    fun `agregarAlumnos throws CursoNotFoundException when curso does not exist`() {
        `when`(cursoRepository.findById(99L)).thenReturn(Optional.empty())

        val exception = assertThrows<com.ar.edu.unq.unqlassroom.errors.CursoNotFoundException> {
            cursoService.agregarAlumnos(99L, AgregarAlumnosRequestDTO(listOf("alumno1")))
        }

        assertEquals("Curso no encontrado", exception.message)
    }

    @Test
    fun `agregarAlumnos throws 400 BAD_REQUEST when curso has no github team slug`() {
        val curso = Curso(
            id = 2L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            githubTeamSlug = null
        )
        `when`(cursoRepository.findById(2L)).thenReturn(Optional.of(curso))

        val exception = assertThrows<CursoSinGitHubTeamAsociadoException> {
            cursoService.agregarAlumnos(2L, AgregarAlumnosRequestDTO(listOf("alumno1")))
        }

        assertEquals("Curso sin GitHub Team asociado", exception.message)
    }

    @Test
    fun `agregarAlumnos no genera repo si el alumno ya tiene un repo para el curso actual pero guarda y retorna repositorio`() {
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            githubTeamId = 123456L,
            githubTeamSlug = "2026s1_c1_estructuras_de_datos"
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val membership1 = GitHubTeamMembershipResponse(
            url = "url/alumno1",
            role = "member",
            state = "active"
        )

        `when`(gitHubTeamService.addMemberToTeam("2026s1_c1_estructuras_de_datos", "alumno1", "member", null))
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

        `when`(alumnoRepository.findByCursoIdAndUsername(1L, "alumno1")).thenReturn(null)
        `when`(alumnoRepository.save(Mockito.any(Alumno::class.java))).thenAnswer { it.getArgument(0) }

        val request = AgregarAlumnosRequestDTO(usernames = listOf("alumno1"))
        val response = cursoService.agregarAlumnos(1L, request)

        assertEquals(1, response.alumnos.size)
        assertEquals("alumno1", response.alumnos[0].username)
        assertNotNull(response.alumnos[0].repositorio)
        assertEquals("Segundo commit", response.alumnos[0].repositorio?.ultimoCommit)
        assertEquals("success", response.alumnos[0].repositorio?.estadoCI)

        verify(gitHubTeamService).addMemberToTeam("2026s1_c1_estructuras_de_datos", "alumno1", "member", null)
        verify(gitHubRepoService, Mockito.never()).createOrgRepository(
            name = Mockito.anyString(),
            description = Mockito.any(),
            private = Mockito.anyBoolean(),
            autoInit = Mockito.anyBoolean(),
            org = Mockito.any()
        )
        verify(gitHubRepoService, Mockito.never()).addCollaborator(
            repoName = Mockito.anyString(),
            username = Mockito.anyString(),
            permission = Mockito.anyString(),
            org = Mockito.any()
        )
        verify(alumnoRepository).save(Mockito.any(Alumno::class.java))
    }

    @Test
    fun `obtenerAlumnos returns members with updated repositorio from GitHub and updates DB entity`() {
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            githubTeamId = 123456L,
            githubTeamSlug = "2026s1_c1_estructuras_de_datos"
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val teamMembers = listOf(
            GitHubTeamMemberResponse(username = "alumno1", role = "member", state = "active"),
            GitHubTeamMemberResponse(username = "alumno2", role = "member", state = "active")
        )
        `when`(gitHubTeamService.getTeamMembers("2026s1_c1_estructuras_de_datos")).thenReturn(teamMembers)

        val alumnoPersistido1 = Alumno(
            username = "alumno1",
            role = "member",
            state = "active",
            curso = curso,
            repositorio = Repositorio(
                nombre = "2026s1_c1_estructuras_de_datos_alumno1",
                htmlUrl = "https://github.com/UNQlassroom/2026s1_c1_estructuras_de_datos_alumno1",
                ultimoCommit = "Fix tests",
                fechaUltimoCommit = "2026-09-09T20:00:00Z",
                estadoCI = "success"
            )
        )
        `when`(alumnoRepository.findByCursoId(1L)).thenReturn(listOf(alumnoPersistido1))

        val updatedRepoInfo = RepositorioInfo(
            nombre = "2026s1_c1_estructuras_de_datos_alumno1",
            htmlUrl = "https://github.com/UNQlassroom/2026s1_c1_estructuras_de_datos_alumno1",
            ultimoCommit = "Nuevo commit",
            fechaUltimoCommit = "2026-09-10T10:00:00Z",
            estadoCI = "failure"
        )
        `when`(gitHubRepoService.obtenerInformacionRepositorio("2026s1_c1_estructuras_de_datos_alumno1"))
            .thenReturn(updatedRepoInfo)

        `when`(gitHubRepoService.generarNombreRepo(curso, "alumno2"))
            .thenReturn("2026s1_c1_estructuras_de_datos_alumno2")
        `when`(gitHubRepoService.repositoryExists("2026s1_c1_estructuras_de_datos_alumno2"))
            .thenReturn(false)

        val response = cursoService.obtenerAlumnos(1L)

        assertEquals(1L, response.cursoId)
        assertEquals(2, response.alumnos.size)

        val a1 = response.alumnos.first { it.username == "alumno1" }
        assertNotNull(a1.repositorio)
        assertEquals("Nuevo commit", a1.repositorio?.ultimoCommit)
        assertEquals("2026-09-10T10:00:00Z", a1.repositorio?.fechaUltimoCommit)
        assertEquals("failure", a1.repositorio?.estadoCI)
        assertEquals("Nuevo commit", alumnoPersistido1.repositorio?.ultimoCommit)

        val a2 = response.alumnos.first { it.username == "alumno2" }
        assertNull(a2.repositorio)
    }

    @Test
    fun `obtenerAlumnos returns members not persisted in DB but with repo existing in GitHub`() {
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            githubTeamId = 123456L,
            githubTeamSlug = "2026s1_c1_estructuras_de_datos"
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val teamMembers = listOf(
            GitHubTeamMemberResponse(username = "alumno_github_only", role = "member", state = "active")
        )
        `when`(gitHubTeamService.getTeamMembers("2026s1_c1_estructuras_de_datos")).thenReturn(teamMembers)
        `when`(alumnoRepository.findByCursoId(1L)).thenReturn(emptyList())

        `when`(gitHubRepoService.generarNombreRepo(curso, "alumno_github_only"))
            .thenReturn("2026s1_c1_estructuras_de_datos_alumno_github_only")
        `when`(gitHubRepoService.repositoryExists("2026s1_c1_estructuras_de_datos_alumno_github_only"))
            .thenReturn(true)

        val repoInfo = RepositorioInfo(
            nombre = "2026s1_c1_estructuras_de_datos_alumno_github_only",
            htmlUrl = "https://github.com/UNQlassroom/2026s1_c1_estructuras_de_datos_alumno_github_only",
            ultimoCommit = "Commit inicial",
            fechaUltimoCommit = "2026-09-10T09:00:00Z",
            estadoCI = "pending"
        )
        `when`(gitHubRepoService.obtenerInformacionRepositorio("2026s1_c1_estructuras_de_datos_alumno_github_only"))
            .thenReturn(repoInfo)

        val response = cursoService.obtenerAlumnos(1L)

        assertEquals(1, response.alumnos.size)
        val a = response.alumnos.first()
        assertEquals("alumno_github_only", a.username)
        assertNotNull(a.repositorio)
        assertEquals("Commit inicial", a.repositorio?.ultimoCommit)
        assertEquals("pending", a.repositorio?.estadoCI)
    }

    @Test
    fun `crearCurso remueve tildes de materia al crear team en github y guardar curso`() {
        val requestDTO = CursoRequestDTO(
            materia = "Programación Funcional",
            anio = 2026,
            semestre = 2,
            comision = 3,
        )

        val teamResponse = GitHubTeamResponse(
            id = 777L,
            nodeId = "MDQ6VGVhbTc3Nw==",
            name = "2026s2_c3_programacion_funcional",
            slug = "2026s2_c3_programacion_funcional",
            description = "Curso de Programación Funcional - Año 2026 - Semestre 2 - Comisión 3"
        )

        `when`(gitHubTeamService.createTeam(
            name = anyString(),
            description = anyString(),
            privacy = anyString(),
            org = Mockito.isNull()
        )).thenReturn(teamResponse)

        `when`(cursoRepository.save(anyCurso())).thenAnswer { invocation ->
            val curso = invocation.getArgument<Curso>(0)
            Curso(
                id = 5L,
                materia = curso.materia,
                anio = curso.anio,
                semestre = curso.semestre,
                comision = curso.comision,
                descripcion = curso.descripcion,
                githubTeamId = curso.githubTeamId,
                githubTeamSlug = curso.githubTeamSlug
            )
        }

        val result = cursoService.crearCurso(requestDTO)

        assertNotNull(result)
        assertEquals("Programación Funcional", result.materia)
        assertEquals("2026s2_c3_programacion_funcional", result.githubTeamSlug)

        verify(gitHubTeamService).createTeam(
            name = "2026s2_c3_programacion_funcional",
            description = "Curso de Programación Funcional - Año 2026 - Semestre 2 - Comisión 3",
            privacy = "closed",
            org = null
        )
    }
}
