package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.dto.curso.request.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.dto.curso.request.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.exception.*
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubOrgMembershipResponse
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubOrgService
import com.ar.edu.unq.unqlassroom.model.Asignacion
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.GrupoAsignacion
import com.ar.edu.unq.unqlassroom.model.Inscripcion
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.TipoAsignacion
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.impl.CursoServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.time.LocalDate
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class CursoServiceImplTest {

    @Mock
    private lateinit var cursoRepository: CursoRepository

    @Mock
    private lateinit var usuarioService: UsuarioService

    @Mock
    private lateinit var usuarioRepository: UsuarioRepository

    @Mock
    private lateinit var inscripcionRepository: InscripcionRepository

    @Mock
    private lateinit var gitHubOrgService: GitHubOrgService

    @Mock
    private lateinit var sseNotificationService: SseNotificationService

    @Mock
    private lateinit var asyncService: AsyncService

    private lateinit var cursoService: CursoServiceImpl

    @BeforeEach
    fun setUp() {
        cursoService = CursoServiceImpl(
            cursoRepository = cursoRepository,
            usuarioService = usuarioService,
            usuarioRepository = usuarioRepository,
            inscripcionRepository = inscripcionRepository,
            gitHubOrgService = gitHubOrgService,
            sseNotificationService = sseNotificationService,
            asyncService = asyncService,
        )
        Mockito.lenient().`when`(gitHubOrgService.obtenerUsernameCanonico(Mockito.anyString())).thenAnswer {
            it.getArgument<String>(0)
        }
    }

    private fun anyCurso(): Curso {
        Mockito.any(Curso::class.java)
        return Curso(materia = "Test", anio = 2026, semestre = 1, comision = 1)
    }

    @Test
    fun `agregarAlumnos invites students to github org and creates inscripcion with org membership state`() {
        val owner = Usuario(id = 10L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        `when`(usuarioService.obtenerOCrearAlumno(Mockito.anyString())).thenAnswer { invocation ->
            val u = invocation.getArgument<String>(0)
            Usuario(id = 1L, username = u, esDocente = false, githubState = "pending")
        }
        `when`(inscripcionRepository.save(Mockito.any(Inscripcion::class.java))).thenAnswer { it.getArgument(0) }

        `when`(gitHubOrgService.invitarMiembro("alumno1")).thenReturn(
            GitHubOrgMembershipResponse(state = "pending", role = "member")
        )
        `when`(gitHubOrgService.invitarMiembro("alumno2")).thenReturn(
            GitHubOrgMembershipResponse(state = "active", role = "member")
        )

        val request = AgregarAlumnosRequestDTO(
            usernames = listOf("alumno1", "alumno2", "alumno1 ", "ALUMNO1")
        )

        val response = cursoService.agregarAlumnos(1L, request.usernames, "profe_test")

        assertEquals(2, response.size)
        assertEquals("alumno1", response[0].usuario.username)
        assertEquals("pending", response[0].usuario.githubState)
        assertEquals("member", response[0].githubRole)

        assertEquals("alumno2", response[1].usuario.username)
        assertEquals("active", response[1].usuario.githubState)
        assertEquals("member", response[1].githubRole)

        verify(gitHubOrgService).invitarMiembro("alumno1")
        verify(gitHubOrgService).invitarMiembro("alumno2")
        verify(inscripcionRepository, Mockito.times(2)).save(Mockito.any(Inscripcion::class.java))
    }

    @Test
    fun `agregarAlumnos saves student with canonical GitHub username when invited with different casing`() {
        val owner = Usuario(id = 10L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(gitHubOrgService.obtenerUsernameCanonico("THIAGOdePRUEBA")).thenReturn("thiagoDePrueba")
        `when`(usuarioService.obtenerOCrearAlumno("thiagoDePrueba")).thenReturn(
            Usuario(id = 1L, username = "thiagoDePrueba", esDocente = false, githubState = "pending")
        )
        `when`(inscripcionRepository.save(Mockito.any(Inscripcion::class.java))).thenAnswer { it.getArgument(0) }
        `when`(gitHubOrgService.invitarMiembro("thiagoDePrueba")).thenReturn(
            GitHubOrgMembershipResponse(state = "pending", role = "member")
        )

        val response = cursoService.agregarAlumnos(1L, listOf("THIAGOdePRUEBA"), "profe_test")

        assertEquals(1, response.size)
        assertEquals("thiagoDePrueba", response[0].usuario.username)
        verify(usuarioService).obtenerOCrearAlumno("thiagoDePrueba")
        verify(gitHubOrgService).invitarMiembro("thiagoDePrueba")
    }

    @Test
    fun `agregarAlumnos throws AlumnoDuplicadoException without calling GitHub when student is already enrolled`() {
        val owner = Usuario(id = 10L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        val alumnoExistente = Usuario(id = 2L, username = "alumno1", esDocente = false, githubState = "active")
        val inscripcionExistente = Inscripcion(id = 5L, curso = curso, usuario = alumnoExistente)

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoId(1L)).thenReturn(listOf(inscripcionExistente))

        val exception = assertThrows<AlumnoDuplicadoException> {
            cursoService.agregarAlumnos(1L, listOf("ALUMNO1"), "profe_test")
        }

        assertEquals("Los siguientes alumnos ya están inscriptos en el curso: alumno1", exception.message)
        assertEquals("ALUMNO_DUPLICADO", exception.errorCode)
        verify(gitHubOrgService, Mockito.never()).obtenerUsernameCanonico(Mockito.anyString())
        verify(gitHubOrgService, Mockito.never()).invitarMiembro(Mockito.anyString(), Mockito.anyString(), Mockito.nullable(String::class.java))
    }

    @Test
    fun `agregarAlumnos fails atomically and throws AlumnoDuplicadoException when any student in batch is already enrolled`() {
        val owner = Usuario(id = 10L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        val alumnoExistente = Usuario(id = 2L, username = "alumno_viejo", esDocente = false, githubState = "active")
        val inscripcionExistente = Inscripcion(id = 5L, curso = curso, usuario = alumnoExistente)

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoId(1L)).thenReturn(listOf(inscripcionExistente))

        val exception = assertThrows<AlumnoDuplicadoException> {
            cursoService.agregarAlumnos(1L, listOf("alumno_nuevo", "ALUMNO_VIEJO"), "profe_test")
        }

        assertEquals("Los siguientes alumnos ya están inscriptos en el curso: alumno_viejo", exception.message)
        assertEquals("ALUMNO_DUPLICADO", exception.errorCode)
        verify(gitHubOrgService, Mockito.never()).obtenerUsernameCanonico(Mockito.anyString())
        verify(gitHubOrgService, Mockito.never()).invitarMiembro(Mockito.anyString(), Mockito.anyString(), Mockito.nullable(String::class.java))
        verify(inscripcionRepository, Mockito.never()).save(Mockito.any(Inscripcion::class.java))
    }

    @Test
    fun `agregarAlumnos throws UsuariosGitHubNoEncontradosException when a username does not exist on GitHub`() {
        val owner = Usuario(id = 10L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(gitHubOrgService.obtenerUsernameCanonico("alumno_fantasma")).thenReturn(null)

        val request = AgregarAlumnosRequestDTO(
            usernames = listOf("alumno_valido", "alumno_fantasma")
        )

        val exception = assertThrows<UsuariosGitHubNoEncontradosException> {
            cursoService.agregarAlumnos(1L, request.usernames, "profe_test")
        }

        assertEquals("Los siguientes usuarios no existen en GitHub: alumno_fantasma", exception.message)
        verify(gitHubOrgService, Mockito.never()).invitarMiembro(Mockito.anyString(), Mockito.anyString(), Mockito.nullable(String::class.java))
    }

    @Test
    fun `agregarAlumnos throws CursoNotFoundException when curso does not exist`() {
        `when`(cursoRepository.findById(99L)).thenReturn(Optional.empty())

        val exception = assertThrows<CursoNotFoundException> {
            cursoService.agregarAlumnos(99L, listOf("alumno1"), "profe_test")
        }

        assertEquals("Curso no encontrado", exception.message)
    }

    @Test
    fun `agregarAlumnos throws NoEsDocenteDelCursoException when solicitante is not the owner`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val exception = assertThrows<NoEsDocenteDelCursoException> {
            cursoService.agregarAlumnos(1L, listOf("alumno1"), "otro_profe")
        }

        assertEquals("Solo el docente a cargo del curso puede agregar alumnos", exception.message)
    }

    @Test
    fun `obtenerAlumnos returns enrolled members from DB without calling github`() {
        val owner = Usuario(id = 10L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val usuario1 = Usuario(id = 1L, username = "alumno1", esDocente = false, githubState = "pending")
        val usuario2 = Usuario(id = 2L, username = "alumno2", esDocente = false, githubState = "active")
        val inscripcion1 = Inscripcion(usuario = usuario1, curso = curso)
        val inscripcion2 = Inscripcion(usuario = usuario2, curso = curso)

        `when`(inscripcionRepository.findByCursoId(1L)).thenReturn(listOf(inscripcion1, inscripcion2))

        val response = cursoService.obtenerAlumnos(1L, "profe_test")

        assertEquals(2, response.size)
        assertEquals("alumno1", response[0].usuario.username)
        assertEquals("pending", response[0].usuario.githubState)
        assertEquals("alumno2", response[1].usuario.username)
        assertEquals("active", response[1].usuario.githubState)

        verify(gitHubOrgService, Mockito.never()).invitarMiembro(Mockito.anyString(), Mockito.anyString(), Mockito.nullable(String::class.java))
    }

    @Test
    fun `crearCurso propagates UsuarioNoEsDocenteException when owner is not docente`() {
        val requestDTO = CursoRequestDTO(
            materia = "Redes",
            anio = 2026,
            semestre = 1,
            comision = 1,
        )
        `when`(usuarioService.obtenerDocente("alumno_infiltrado"))
            .thenThrow(UsuarioNoEsDocenteException("alumno_infiltrado"))

        val ex = assertThrows<UsuarioNoEsDocenteException> {
            cursoService.crearCurso(requestDTO.aModelo(), "alumno_infiltrado")
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
            cursoService.crearCurso(requestDTO.aModelo(), "fantasma")
        }
        assertEquals("Usuario no encontrado: fantasma", ex.message)
    }

    @Test
    fun `Usuario defaults githubState to pending`() {
        val usuario = Usuario(username = "alumno1")
        assertEquals("pending", usuario.githubState)
    }

    @Test
    fun `obtenerAlumnos returns members when solicitante is enrolled alumno`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        val alumno = Usuario(id = 2L, username = "alumno_inscripto", esDocente = false)
        val inscripcion = Inscripcion(curso = curso, usuario = alumno)
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(1L, "alumno_inscripto")).thenReturn(inscripcion)
        `when`(inscripcionRepository.findByCursoId(1L)).thenReturn(emptyList())

        val response = cursoService.obtenerAlumnos(1L, "alumno_inscripto")
        assertNotNull(response)
        assertEquals(0, response.size)
    }

    @Test
    fun `obtenerAlumnos throws SinPermisoAccesoCursoException when solicitante is neither owner nor enrolled alumno`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(1L, "infiltrado")).thenReturn(null)

        val ex = assertThrows<SinPermisoAccesoCursoException> {
            cursoService.obtenerAlumnos(1L, "infiltrado")
        }
        assertEquals("No tiene permisos para ver los alumnos de este curso", ex.message)
    }

    @Test
    fun `obtenerCursos for docente calls findCursosParaDocente and returns DTOs`() {
        val curso = Curso(id = 1L, materia = "Estructuras", anio = 2026, semestre = 1, comision = 1)
        `when`(cursoRepository.findCursosParaDocente("profe_test", false)).thenReturn(listOf(curso))

        val result = cursoService.obtenerCursos("profe_test", true)

        assertEquals(1, result.size)
        assertEquals("Estructuras", result[0].materia)
        verify(cursoRepository).findCursosParaDocente("profe_test", false)
        verify(cursoRepository, Mockito.never()).findCursosParaAlumno(Mockito.anyString())
    }

    @Test
    fun `obtenerCursos for alumno calls findCursosParaAlumno and returns DTOs`() {
        val curso = Curso(id = 2L, materia = "Redes", anio = 2026, semestre = 1, comision = 2)
        `when`(cursoRepository.findCursosParaAlumno("alumno_test")).thenReturn(listOf(curso))

        val result = cursoService.obtenerCursos("alumno_test", false)

        assertEquals(1, result.size)
        assertEquals("Redes", result[0].materia)
        verify(cursoRepository).findCursosParaAlumno("alumno_test")
        verify(cursoRepository, Mockito.never()).findCursosParaDocente(Mockito.anyString(), Mockito.anyBoolean())
    }

    @Test
    fun `obtenerCursos with archivados true returns archived courses for docente and empty list for alumno`() {
        val cursoArchivado = Curso(id = 1L, materia = "Estructuras", anio = 2025, semestre = 1, comision = 1, archivado = true)
        `when`(cursoRepository.findCursosParaDocente("profe_test", true)).thenReturn(listOf(cursoArchivado))

        val resultDocente = cursoService.obtenerCursos("profe_test", true, archivados = true)
        assertEquals(1, resultDocente.size)
        assertTrue(resultDocente[0].archivado)

        val resultAlumno = cursoService.obtenerCursos("alumno_test", false, archivados = true)
        assertTrue(resultAlumno.isEmpty())
    }

    @Test
    fun `obtenerCurso returns curso when solicitante is owner`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            descripcion = "desc",
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val result = cursoService.obtenerCurso(1L, "profe_owner")

        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals("Estructuras de Datos", result.materia)
        assertEquals("profe_owner", result.owner?.username)
    }

    @Test
    fun `obtenerCurso returns curso when solicitante is enrolled alumno`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            descripcion = "desc",
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        val alumno = Usuario(id = 2L, username = "alumno_inscripto", esDocente = false)
        val inscripcion = Inscripcion(curso = curso, usuario = alumno)
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(1L, "alumno_inscripto")).thenReturn(inscripcion)

        val result = cursoService.obtenerCurso(1L, "alumno_inscripto")

        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals("Estructuras de Datos", result.materia)
    }

    @Test
    fun `obtenerCurso throws SinPermisoAccesoCursoException when curso is archived and solicitante is enrolled alumno`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            archivado = true,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        assertThrows<SinPermisoAccesoCursoException> {
            cursoService.obtenerCurso(1L, "alumno_inscripto")
        }
        assertEquals(1L, cursoService.obtenerCurso(1L, "profe_owner").id)
    }

    @Test
    fun `obtenerCurso throws CursoNotFoundException when curso does not exist`() {
        `when`(cursoRepository.findById(999L)).thenReturn(Optional.empty())

        val ex = assertThrows<CursoNotFoundException> {
            cursoService.obtenerCurso(999L, "cualquiera")
        }
        assertEquals("Curso no encontrado", ex.message)
    }

    @Test
    fun `obtenerCurso throws SinPermisoAccesoCursoException when solicitante is neither owner nor enrolled alumno`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(1L, "infiltrado")).thenReturn(null)

        val ex = assertThrows<SinPermisoAccesoCursoException> {
            cursoService.obtenerCurso(1L, "infiltrado")
        }
        assertEquals("No tiene permisos para acceder a este curso", ex.message)
    }

    @Test
    fun `crearCurso throws CursoDuplicadoException when duplicate curso exists for owner`() {
        val docente = Usuario(id = 10L, username = "profe_test", esDocente = true)
        `when`(usuarioService.obtenerDocente("profe_test")).thenReturn(docente)
        `when`(cursoRepository.existsByOwnerUsernameAndMateriaIgnoreCaseAndAnioAndSemestreAndComision(
            "profe_test", "Estructuras de Datos", 2026, 1, 1
        )).thenReturn(true)

        val requestDTO = CursoRequestDTO(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
        )

        val ex = assertThrows<CursoDuplicadoException> {
            cursoService.crearCurso(requestDTO.aModelo(), "profe_test")
        }
        assertEquals("Ya existe un curso para la materia 'Estructuras de Datos' en el año 2026, semestre 1 y comisión 1", ex.message)
    }

    @Test
    fun `Curso constructor throws domain exceptions on invalid attributes`() {
        assertThrows<NombreMateriaVacioException> {
            Curso(materia = "", anio = 2026, semestre = 1, comision = 1)
        }
        assertThrows<AnioCursoInvalidoException> {
            Curso(materia = "BD", anio = 1999, semestre = 1, comision = 1)
        }
        assertThrows<SemestreInvalidoException> {
            Curso(materia = "BD", anio = 2026, semestre = 3, comision = 1)
        }
        assertThrows<ComisionInvalidaException> {
            Curso(materia = "BD", anio = 2026, semestre = 1, comision = 0)
        }
    }

    @Test
    fun `archivarCurso marks curso as archived and triggers async GitHub repository archiving`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 1L, materia = "Estructuras", anio = 2026, semestre = 1, comision = 1, owner = owner)
        val asignacion = Asignacion(
            id = 5L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso,
        )
        val grupo = GrupoAsignacion(
            id = 10L,
            asignacion = asignacion,
            repositorio = Repositorio(nombre = "repo-tp1-a1", htmlUrl = "https://github.com/org/repo-tp1-a1"),
        )
        asignacion.grupos.add(grupo)
        curso.asignaciones.add(asignacion)

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(cursoRepository.save(anyCurso())).thenAnswer { it.getArgument(0) }

        val resultado = cursoService.archivarCurso(1L, "profe_owner")

        assertTrue(resultado.archivado)
        verify(cursoRepository).save(curso)
        verify(asyncService).archivarRepositoriosAsincronicamente(listOf("repo-tp1-a1"))
    }

    @Test
    fun `archivarCurso throws CursoArchivadoException when already archived and NoEsDocenteDelCursoException when not owner`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(id = 1L, materia = "Estructuras", anio = 2026, semestre = 1, comision = 1, owner = owner)
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        assertThrows<NoEsDocenteDelCursoException> {
            cursoService.archivarCurso(1L, "otro_profe")
        }

        curso.archivado = true
        assertThrows<CursoArchivadoException> {
            cursoService.archivarCurso(1L, "profe_owner")
        }
        assertThrows<CursoArchivadoException> {
            cursoService.agregarAlumnos(1L, listOf("alumno1"), "profe_owner")
        }
    }

    @Test
    fun `archivarCursosVencidosAutomaticamente archives only expired courses`() {
        val cursoVencido = Curso(id = 1L, materia = "Algoritmos", anio = 2025, semestre = 2, comision = 1)
        val cursoVigente = Curso(id = 2L, materia = "Redes", anio = 2026, semestre = 1, comision = 1)
        `when`(cursoRepository.findByArchivadoFalse()).thenReturn(listOf(cursoVencido, cursoVigente))
        `when`(cursoRepository.save(anyCurso())).thenAnswer { it.getArgument(0) }

        cursoService.archivarCursosVencidosAutomaticamente(LocalDate.of(2026, 3, 1))

        assertTrue(cursoVencido.archivado)
        assertFalse(cursoVigente.archivado)
    }
}
