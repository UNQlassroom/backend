package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.exception.*
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubOrgMembershipResponse
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubOrgService
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Inscripcion
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.impl.CursoServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class CursoDocentesServiceImplTest {

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

    @Mock
    private lateinit var cursoExportService: CursoExportService

    private lateinit var cursoService: CursoServiceImpl

    @BeforeEach
    fun setUp() {
        cursoService = CursoServiceImpl(
            cursoRepository = cursoRepository,
            usuarioService = usuarioService,
            usuarioRepository = usuarioRepository,
            inscripcionRepository = inscripcionRepository,
            gitHubOrgService = gitHubOrgService,
            cursoExportService = cursoExportService,
            sseNotificationService = sseNotificationService,
            asyncService = asyncService,
        )
    }

    @Test
    fun `agregarDocentes agrega y promueve docente exitosamente aunque haya sido alumno`() {
        val docenteOwner = Usuario(id = 10L, username = "profe_owner")
        val docentePromovido = Usuario(id = 11L, username = "rodribolanos", githubState = "active")
        val curso = Curso(
            id = 1L,
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docenteOwner)
        )

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(gitHubOrgService.obtenerUsernameCanonico("rodribolanos")).thenReturn("rodribolanos")
        `when`(usuarioService.obtenerOCrearDocente("rodribolanos")).thenReturn(docentePromovido)
        `when`(gitHubOrgService.invitarMiembro("rodribolanos", role = "member")).thenReturn(
            GitHubOrgMembershipResponse(state = "active", role = "member")
        )
        `when`(cursoRepository.save(curso)).thenReturn(curso)

        val resultado = cursoService.agregarDocentes(1L, listOf("rodribolanos"), "profe_owner")

        assertEquals(2, resultado.size)
        assertTrue(resultado.any { it.username == "rodribolanos" })
        verify(cursoRepository).save(curso)
        verify(sseNotificationService).notificarMiembroCursoActualizado(
            cursoId = 1L,
            docentesUsernames = listOf("profe_owner", "rodribolanos"),
            alumnosUsernames = emptyList()
        )
    }

    @Test
    fun `agregarDocentes permite agregar como docente a un usuario que fue alumno del curso pero fue dado de baja`() {
        val docenteOwner = Usuario(id = 10L, username = "profe_owner")
        val exAlumno = Usuario(id = 15L, username = "ex_alumno", githubState = "active")
        val curso = Curso(
            id = 1L,
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docenteOwner)
        )
        // Inscripción inactiva (dado de baja)
        val inscripcionInactiva = Inscripcion(id = 20L, curso = curso, usuario = exAlumno, activa = false)

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndActivaTrue(1L)).thenReturn(emptyList())
        `when`(gitHubOrgService.obtenerUsernameCanonico("ex_alumno")).thenReturn("ex_alumno")
        `when`(usuarioService.obtenerOCrearDocente("ex_alumno")).thenReturn(exAlumno)
        `when`(gitHubOrgService.invitarMiembro("ex_alumno", role = "member")).thenReturn(
            GitHubOrgMembershipResponse(state = "active", role = "member")
        )
        `when`(cursoRepository.save(curso)).thenReturn(curso)

        val resultado = cursoService.agregarDocentes(1L, listOf("ex_alumno"), "profe_owner")

        assertEquals(2, resultado.size)
        assertTrue(resultado.any { it.username == "ex_alumno" })
        assertTrue(curso.esDocente("ex_alumno"))
        verify(gitHubOrgService).invitarMiembro("ex_alumno", role = "member")
    }

    @Test
    fun `agregarDocentes lanza DocenteDuplicadoException si docente ya esta asignado`() {
        val docenteOwner = Usuario(id = 10L, username = "profe_owner")
        val curso = Curso(
            id = 1L,
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docenteOwner)
        )

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        assertThrows<DocenteDuplicadoException> {
            cursoService.agregarDocentes(1L, listOf("profe_owner"), "profe_owner")
        }
    }

    @Test
    fun `agregarDocentes lanza DocenteEsAlumnoDelCursoException si usuario es alumno inscripto activo del mismo curso`() {
        val docenteOwner = Usuario(id = 10L, username = "profe_owner")
        val alumnoUsuario = Usuario(id = 15L, username = "alumno_cursante")
        val curso = Curso(
            id = 1L,
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docenteOwner)
        )
        val inscripcionActiva = Inscripcion(id = 20L, curso = curso, usuario = alumnoUsuario, activa = true)

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndActivaTrue(1L)).thenReturn(listOf(inscripcionActiva))

        val ex = assertThrows<DocenteEsAlumnoDelCursoException> {
            cursoService.agregarDocentes(1L, listOf("ALUMNO_CURSANTE"), "profe_owner")
        }
        assertEquals("DOCENTE_ES_ALUMNO_DEL_CURSO", ex.errorCode)
        assertTrue(ex.message!!.contains("ALUMNO_CURSANTE"))
    }

    @Test
    fun `agregarAlumnos lanza AlumnoEsDocenteDelCursoException si usuario ya es docente del curso`() {
        val docenteOwner = Usuario(id = 10L, username = "profe_owner")
        val curso = Curso(
            id = 1L,
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docenteOwner)
        )

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val ex = assertThrows<AlumnoEsDocenteDelCursoException> {
            cursoService.agregarAlumnos(1L, listOf("PROFE_OWNER"), "profe_owner")
        }
        assertEquals("ALUMNO_ES_DOCENTE_DEL_CURSO", ex.errorCode)
        assertTrue(ex.message!!.contains("PROFE_OWNER"))
    }

    @Test
    fun `agregarDocentes lanza UsuariosGitHubNoEncontradosException si usuario no existe en github`() {
        val docenteOwner = Usuario(id = 10L, username = "profe_owner")
        val curso = Curso(
            id = 1L,
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docenteOwner)
        )

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(gitHubOrgService.obtenerUsernameCanonico("fantasma")).thenReturn(null)

        assertThrows<UsuariosGitHubNoEncontradosException> {
            cursoService.agregarDocentes(1L, listOf("fantasma"), "profe_owner")
        }
    }

    @Test
    fun `agregarDocentes lanza NoEsDocenteDelCursoException si solicitante no es docente del curso`() {
        val docenteOwner = Usuario(id = 10L, username = "profe_owner")
        val curso = Curso(
            id = 1L,
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docenteOwner)
        )

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        assertThrows<NoEsDocenteDelCursoException> {
            cursoService.agregarDocentes(1L, listOf("profe2"), "alumno_random")
        }
    }

    @Test
    fun `obtenerDocentes retorna lista de docentes del curso`() {
        val docenteOwner = Usuario(id = 10L, username = "profe_owner")
        val curso = Curso(
            id = 1L,
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docenteOwner)
        )

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val resultado = cursoService.obtenerDocentes(1L, "profe_owner")

        assertEquals(1, resultado.size)
        assertEquals("profe_owner", resultado[0].username)
    }

    @Test
    fun `darDeBajaDocente remueve docente exitosamente y notifica a todos los docentes`() {
        val docenteOwner = Usuario(id = 10L, username = "profe_owner")
        val docente2 = Usuario(id = 11L, username = "profe_adjunto")
        val curso = Curso(
            id = 1L,
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docenteOwner, docente2)
        )

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(cursoRepository.save(curso)).thenReturn(curso)

        cursoService.darDeBajaDocente(1L, "profe_adjunto", "profe_owner")

        assertFalse(curso.esDocente("profe_adjunto"))
        verify(cursoRepository).save(curso)
        verify(sseNotificationService).notificarMiembroCursoActualizado(
            cursoId = 1L,
            docentesUsernames = listOf("profe_owner"),
            alumnosUsernames = listOf("profe_adjunto")
        )
    }

    @Test
    fun `darDeBajaDocente lanza CursoSinDocentesException si intenta remover al unico docente`() {
        val docenteOwner = Usuario(id = 10L, username = "profe_owner")
        val curso = Curso(
            id = 1L,
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docenteOwner)
        )

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        assertThrows<CursoSinDocentesException> {
            cursoService.darDeBajaDocente(1L, "profe_owner", "profe_owner")
        }
    }

    @Test
    fun `darDeBajaDocente lanza DocenteNoAsignadoException si docente no pertenece al curso`() {
        val docenteOwner = Usuario(id = 10L, username = "profe_owner")
        val docente2 = Usuario(id = 11L, username = "profe_adjunto")
        val curso = Curso(
            id = 1L,
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docenteOwner, docente2)
        )

        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        assertThrows<DocenteNoAsignadoException> {
            cursoService.darDeBajaDocente(1L, "profe_inexistente", "profe_owner")
        }
    }
}
