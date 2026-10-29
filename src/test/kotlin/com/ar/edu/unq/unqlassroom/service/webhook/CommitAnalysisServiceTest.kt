package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.service.CommitAnalysisService
import com.ar.edu.unq.unqlassroom.service.impl.CommitAnalysisServiceImpl

import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.AdvertenciaGitRepository
import com.ar.edu.unq.unqlassroom.repository.GrupoAsignacionRepository
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
class CommitAnalysisServiceTest {

    @Mock
    private lateinit var advertenciaGitRepository: AdvertenciaGitRepository

    @Mock
    private lateinit var grupoAsignacionRepository: GrupoAsignacionRepository

    @Mock
    private lateinit var usuarioRepository: UsuarioRepository

    private val objectMapper = ObjectMapper()
    private lateinit var service: CommitAnalysisService

    @BeforeEach
    fun setUp() {
        service = CommitAnalysisServiceImpl(
            advertenciaGitRepository = advertenciaGitRepository,
            grupoAsignacionRepository = grupoAsignacionRepository,
            usuarioRepository = usuarioRepository,
            maxFiles = 15,
            maxLines = 500,
        )
    }

    private fun anyAdvertencia(): AdvertenciaGit {
        any(AdvertenciaGit::class.java)
        return AdvertenciaGit(
            commitSha = "dummy",
            mensajeCommit = "dummy",
            tipo = TipoAdvertenciaGit.COMMIT_NO_ATOMICO,
            mensajeExplicativo = "dummy",
            repositorio = Repositorio(nombre = "r", htmlUrl = "h"),
        )
    }

    @Test
    fun `analizarCommitsDePush genera COMMIT_NO_ATOMICO cuando supera umbral de 15 archivos y asocia alumno, grupo y asignacion`() {
        val alumno = Usuario(id = 10L, username = "juanperez")
        val curso = Curso(id = 1L, materia = "Objetos 1", anio = 2026, semestre = 2, comision = 1)
        val asignacion = Asignacion(
            id = 5L,
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = curso
        )
        val repo = Repositorio(id = 100L, nombre = "tp1-juanperez", htmlUrl = "https://github.com/org/tp1-juanperez")
        val grupo = GrupoAsignacion(
            id = 50L,
            asignacion = asignacion,
            repositorio = repo,
            integrantes = mutableListOf(alumno)
        )

        `when`(grupoAsignacionRepository.findByRepositorioNombreIgnoreCase("tp1-juanperez")).thenReturn(grupo)
        `when`(advertenciaGitRepository.save(anyAdvertencia())).thenAnswer { it.getArgument(0) }

        val addedFilesJson = (1..16).joinToString(",") { "\"src/File$it.kt\"" }
        val payload = """
        {
          "repository": { "name": "tp1-juanperez" },
          "commits": [
            {
              "id": "abc1234",
              "message": "feat: agregar todas las clases del dominio",
              "timestamp": "2026-10-20T10:00:00Z",
              "author": { "username": "juanperez" },
              "added": [$addedFilesJson],
              "removed": [],
              "modified": [],
              "additions": 120,
              "deletions": 10
            }
          ]
        }
        """.trimIndent()

        val rootNode = objectMapper.readTree(payload)
        val resultado = service.analizarCommitsDePush(repo, rootNode)

        assertEquals(1, resultado.size)
        val adv = resultado[0]
        assertEquals(TipoAdvertenciaGit.COMMIT_NO_ATOMICO, adv.tipo)
        assertEquals("abc1234", adv.commitSha)
        assertEquals(16, adv.archivosModificados)
        assertEquals(130, adv.lineasModificadas)
        assertEquals(120, adv.lineasAgregadas)
        assertEquals(10, adv.lineasEliminadas)
        assertEquals(alumno, adv.alumno)
        assertEquals(grupo, adv.grupoAsignacion)
        assertEquals(asignacion, adv.asignacion)
        assertEquals(repo, adv.repositorio)
        assertTrue(adv.mensajeExplicativo.contains("Detectamos un commit grande (+130 líneas / 16 archivos)"))
    }

    @Test
    fun `analizarCommitsDePush genera COMMIT_NO_ATOMICO cuando supera umbral de 500 lineas modificadas`() {
        val repo = Repositorio(id = 100L, nombre = "tp1-ana", htmlUrl = "https://github.com/org/tp1-ana")
        `when`(advertenciaGitRepository.save(anyAdvertencia())).thenAnswer { it.getArgument(0) }

        val payload = """
        {
          "repository": { "name": "tp1-ana" },
          "commits": [
            {
              "id": "sha999",
              "message": "feat: implementar logica completa del juego",
              "timestamp": "2026-10-21T12:00:00Z",
              "modified": ["src/Game.kt", "src/Board.kt"],
              "stats": {
                "additions": 420,
                "deletions": 110
              }
            }
          ]
        }
        """.trimIndent()

        val resultado = service.analizarCommitsDePush(repo, objectMapper.readTree(payload))

        assertEquals(1, resultado.size)
        val adv = resultado[0]
        assertEquals(TipoAdvertenciaGit.COMMIT_NO_ATOMICO, adv.tipo)
        assertEquals(2, adv.archivosModificados)
        assertEquals(530, adv.lineasModificadas)
        assertEquals(420, adv.lineasAgregadas)
        assertEquals(110, adv.lineasEliminadas)
        assertEquals(
            "Detectamos un commit grande (+530 líneas / 2 archivos). Se recomienda partir los cambios en commits atómicos centrados en una sola responsabilidad.",
            adv.mensajeExplicativo
        )
    }

    @Test
    fun `analizarCommitsDePush omite commits de init y merge de la validacion de tamano`() {
        val repo = Repositorio(id = 100L, nombre = "tp1-equipo", htmlUrl = "https://github.com/org/tp1-equipo")

        val addedFilesJson = (1..25).joinToString(",") { "\"src/Init$it.kt\"" }
        val payload = """
        {
          "repository": { "name": "tp1-equipo" },
          "commits": [
            {
              "id": "init001",
              "message": "Initial commit",
              "added": [$addedFilesJson],
              "additions": 1200,
              "deletions": 0
            },
            {
              "id": "init002",
              "message": "init",
              "added": [$addedFilesJson],
              "additions": 800,
              "deletions": 0
            },
            {
              "id": "merge001",
              "message": "Merge pull request #12 from org/feature-rama",
              "modified": [$addedFilesJson],
              "additions": 600,
              "deletions": 300
            },
            {
              "id": "merge002",
              "message": "Merge branch 'main' into develop",
              "modified": [$addedFilesJson],
              "additions": 700,
              "deletions": 100
            }
          ]
        }
        """.trimIndent()

        val resultado = service.analizarCommitsDePush(repo, objectMapper.readTree(payload))

        assertTrue(resultado.isEmpty())
        verify(advertenciaGitRepository, never()).save(anyAdvertencia())
    }

    @Test
    fun `analizarCommitsDePush detecta mensajes vagos, cortos o sin Conventional Commits y sugiere el formato`() {
        val repo = Repositorio(id = 100L, nombre = "tp1-carlos", htmlUrl = "https://github.com/org/tp1-carlos")
        `when`(advertenciaGitRepository.save(anyAdvertencia())).thenAnswer { it.getArgument(0) }

        val payload = """
        {
          "repository": { "name": "tp1-carlos" },
          "commits": [
            {
              "id": "c1",
              "message": "fix",
              "modified": ["a.kt"],
              "additions": 5,
              "deletions": 2
            },
            {
              "id": "c2",
              "message": "asd",
              "modified": ["b.kt"],
              "additions": 3,
              "deletions": 1
            },
            {
              "id": "c3",
              "message": "cambios",
              "modified": ["c.kt"],
              "additions": 10,
              "deletions": 0
            },
            {
              "id": "c4",
              "message": "feat: implementar calculadora de impuestos",
              "modified": ["d.kt"],
              "additions": 20,
              "deletions": 5
            },
            {
              "id": "c5",
              "message": "test(impuestos): agregar casos borde de IVA",
              "modified": ["e.kt"],
              "additions": 30,
              "deletions": 0
            }
          ]
        }
        """.trimIndent()

        val resultado = service.analizarCommitsDePush(repo, objectMapper.readTree(payload))

        assertEquals(3, resultado.size)
        assertTrue(resultado.all { it.tipo == TipoAdvertenciaGit.MENSAJE_INADECUADO })
        assertEquals(listOf("c1", "c2", "c3"), resultado.map { it.commitSha })
        assertTrue(resultado.all { it.mensajeExplicativo.contains("Conventional Commits (feat:, fix:, docs:, test:)") })
    }

    @Test
    fun `analizarCommitsDePush genera ambas advertencias si un mismo commit es gigante y tiene mensaje vago`() {
        val repo = Repositorio(id = 100L, nombre = "tp1-mixto", htmlUrl = "https://github.com/org/tp1-mixto")
        `when`(advertenciaGitRepository.save(anyAdvertencia())).thenAnswer { it.getArgument(0) }

        val payload = """
        {
          "repository": { "name": "tp1-mixto" },
          "commits": [
            {
              "id": "c_ambos",
              "message": "wip",
              "modified": ["a.kt", "b.kt"],
              "lines_added": 400,
              "lines_removed": 150
            }
          ]
        }
        """.trimIndent()

        val resultado = service.analizarCommitsDePush(repo, objectMapper.readTree(payload))

        assertEquals(2, resultado.size)
        assertTrue(resultado.any { it.tipo == TipoAdvertenciaGit.COMMIT_NO_ATOMICO })
        assertTrue(resultado.any { it.tipo == TipoAdvertenciaGit.MENSAJE_INADECUADO })
    }

    @Test
    fun `analizarCommitsDePush no rompe ni propaga excepcion si falla la persistencia en el repositorio`() {
        val repo = Repositorio(id = 100L, nombre = "tp1-error", htmlUrl = "https://github.com/org/tp1-error")
        `when`(advertenciaGitRepository.save(anyAdvertencia())).thenThrow(RuntimeException("DB Connection error"))

        val payload = """
        {
          "repository": { "name": "tp1-error" },
          "commits": [
            {
              "id": "c1",
              "message": "asd",
              "modified": ["a.kt"]
            }
          ]
        }
        """.trimIndent()

        val resultado = assertDoesNotThrow<List<AdvertenciaGit>> {
            service.analizarCommitsDePush(repo, objectMapper.readTree(payload))
        }
        assertTrue(resultado.isEmpty())
    }
}

