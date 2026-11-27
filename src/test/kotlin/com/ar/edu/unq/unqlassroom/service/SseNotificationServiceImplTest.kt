package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.dto.sse.SseEventType
import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.repository.GrupoAsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.impl.SseNotificationServiceImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class SseNotificationServiceImplTest {

    @Mock
    private lateinit var grupoAsignacionRepository: GrupoAsignacionRepository

    @Mock
    private lateinit var inscripcionRepository: InscripcionRepository

    @Mock
    private lateinit var usuarioRepository: UsuarioRepository

    @Mock
    private lateinit var cursoRepository: CursoRepository

    private lateinit var sseNotificationService: SseNotificationServiceImpl

    @BeforeEach
    fun setUp() {
        sseNotificationService = SseNotificationServiceImpl(
            grupoAsignacionRepository = grupoAsignacionRepository,
            inscripcionRepository = inscripcionRepository,
            usuarioRepository = usuarioRepository,
            cursoRepository = cursoRepository,
        )
    }

    @Test
    fun `suscribir crea un SseEmitter y envia connected`() {
        val emitter = sseNotificationService.suscribir("docente1")
        assertNotNull(emitter)
    }

    @Test
    fun `notificarEventoDeRepositorio encuentra grupo y notifica a docentes y alumnos`() {
        val docente = Usuario(id = 1L, username = "docente1")
        val alumno1 = Usuario(id = 2L, username = "alumno1")
        val alumno2 = Usuario(id = 3L, username = "alumno2")

        val curso = Curso(
            id = 10L,
            materia = "Objetos 1",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docente),
        )

        val asignacion = Asignacion(
            id = 20L,
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "template-tp1",
            curso = curso,
        )

        val repo = Repositorio(
            id = 30L,
            nombre = "2026s1_c1_objetos_tp1_grupo1",
            htmlUrl = "https://github.com/org/repo",
        )

        val grupo = GrupoAsignacion(
            id = 40L,
            nombre = "Grupo 1",
            asignacion = asignacion,
            repositorio = repo,
            integrantes = mutableListOf(alumno1, alumno2),
        )

        `when`(grupoAsignacionRepository.findByRepositorioNombreIgnoreCase("2026s1_c1_objetos_tp1_grupo1"))
            .thenReturn(grupo)

        // Suscribimos a docente1 y alumno1
        val emitterDocente = sseNotificationService.suscribir("docente1")
        val emitterAlumno = sseNotificationService.suscribir("alumno1")

        assertDoesNotThrow {
            sseNotificationService.notificarEventoDeRepositorio(
                "2026s1_c1_objetos_tp1_grupo1",
                SseEventType.PUSH_RECIBIDO
            )
        }
    }

    @Test
    fun `notificarMiembroActivadoGlobal notifica a los cursos donde esta inscripto el alumno`() {
        val docente = Usuario(id = 1L, username = "docente1")
        val alumno = Usuario(id = 2L, username = "alumno1")

        val curso = Curso(
            id = 10L,
            materia = "Objetos 1",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docente),
        )

        val inscripcion = Inscripcion(
            id = 100L,
            curso = curso,
            usuario = alumno,
        )

        `when`(inscripcionRepository.findByUsuarioUsernameIgnoreCase("alumno1"))
            .thenReturn(listOf(inscripcion))
        `when`(cursoRepository.findCursosParaDocente("alumno1", archivado = false))
            .thenReturn(emptyList())

        sseNotificationService.suscribir("docente1")
        sseNotificationService.suscribir("alumno1")

        assertDoesNotThrow {
            sseNotificationService.notificarMiembroActivadoGlobal("alumno1")
        }
    }

    @Test
    fun `notificarMiembroActivadoGlobal notifica a los cursos donde el usuario es docente`() {
        val docente1 = Usuario(id = 1L, username = "docente1")
        val docente2 = Usuario(id = 2L, username = "docente2")

        val curso = Curso(
            id = 10L,
            materia = "Objetos 1",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docente1, docente2),
        )

        `when`(inscripcionRepository.findByUsuarioUsernameIgnoreCase("docente1"))
            .thenReturn(emptyList())
        `when`(cursoRepository.findCursosParaDocente("docente1", archivado = false))
            .thenReturn(listOf(curso))

        sseNotificationService.suscribir("docente1")
        sseNotificationService.suscribir("docente2")

        assertDoesNotThrow {
            sseNotificationService.notificarMiembroActivadoGlobal("docente1")
        }
    }

    @Test
    fun `notificarMiembroCursoActualizado notifica a todos los docentes y alumnos indicados`() {
        sseNotificationService.suscribir("docente1")
        sseNotificationService.suscribir("docente2")
        sseNotificationService.suscribir("alumno1")

        assertDoesNotThrow {
            sseNotificationService.notificarMiembroCursoActualizado(
                cursoId = 1L,
                docentesUsernames = listOf("docente1", "docente2"),
                alumnosUsernames = listOf("alumno1")
            )
        }
    }

    @Test
    fun `notificarTemplatesActualizados notifica a todos los docentes`() {
        `when`(cursoRepository.findDocentesUsernames())
            .thenReturn(listOf("docente1", "docente2"))

        sseNotificationService.suscribir("docente1")

        assertDoesNotThrow {
            sseNotificationService.notificarTemplatesActualizados("template-algo")
        }
    }

    @Test
    fun `enviarHeartbeat no arroja excepciones con conexiones activas o vacias`() {
        assertDoesNotThrow {
            sseNotificationService.enviarHeartbeat()
        }

        sseNotificationService.suscribir("usuario1")

        assertDoesNotThrow {
            sseNotificationService.enviarHeartbeat()
        }
    }
}
