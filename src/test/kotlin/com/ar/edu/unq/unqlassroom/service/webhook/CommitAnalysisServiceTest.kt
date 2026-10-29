package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.repository.AdvertenciaGitRepository
import com.ar.edu.unq.unqlassroom.repository.GrupoAsignacionRepository
import com.ar.edu.unq.unqlassroom.repository.UsuarioRepository
import com.ar.edu.unq.unqlassroom.service.CommitAnalysisService
import com.ar.edu.unq.unqlassroom.service.impl.CommitAnalysisServiceImpl
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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
            maxLines = 300,
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

        val addedFilesJson = (1..16).joinToString(",") { "\"src/domain/Clase$it.java\"" }
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
        assertTrue(adv.mensajeExplicativo.contains("Detectamos un commit grande (16 archivos modificados)"))
    }

    @Test
    fun `analizarCommitsDePush genera COMMIT_NO_ATOMICO cuando supera umbral de 300 lineas aunque sean pocos archivos`() {
        val repo = Repositorio(id = 100L, nombre = "tp1-lineas", htmlUrl = "https://github.com/org/tp1-lineas")
        `when`(advertenciaGitRepository.save(anyAdvertencia())).thenAnswer { it.getArgument(0) }

        val payload = """
        {
          "repository": { "name": "tp1-lineas" },
          "commits": [
            {
              "id": "c_lineas_gigante",
              "message": "feat: agregar módulo gigante con 450 lineas",
              "timestamp": "2026-10-21T10:00:00Z",
              "modified": ["src/Main.java", "src/Util.java"],
              "stats": {
                "additions": 400,
                "deletions": 50,
                "total": 450,
                "files_changed": 2
              }
            }
          ]
        }
        """.trimIndent()

        val resultado = service.analizarCommitsDePush(repo, objectMapper.readTree(payload))

        assertEquals(1, resultado.size)
        val adv = resultado[0]
        assertEquals(TipoAdvertenciaGit.COMMIT_NO_ATOMICO, adv.tipo)
        assertEquals(450, adv.lineasModificadas)
        assertEquals(400, adv.lineasAgregadas)
        assertEquals(50, adv.lineasEliminadas)
        assertEquals(2, adv.archivosModificados)
        assertTrue(adv.mensajeExplicativo.contains("450 líneas modificadas"))
    }

    @Test
    fun `analizarCommitsDePush no genera COMMIT_NO_ATOMICO si no supera el umbral de 15 archivos ni de 300 lineas`() {
        val repo = Repositorio(id = 100L, nombre = "tp1-ana", htmlUrl = "https://github.com/org/tp1-ana")

        val payload = """
        {
          "repository": { "name": "tp1-ana" },
          "commits": [
            {
              "id": "sha999",
              "message": "feat: implementar logica completa del juego",
              "timestamp": "2026-10-21T12:00:00Z",
              "modified": ["src/Game.kt", "src/Board.kt"]
            }
          ]
        }
        """.trimIndent()

        val resultado = service.analizarCommitsDePush(repo, objectMapper.readTree(payload))

        assertEquals(0, resultado.size)
    }

    @Test
    fun `analizarCommitsDePush omite commits de init y merge de la validacion de tamano`() {
        val repo = Repositorio(id = 100L, nombre = "tp1-equipo", htmlUrl = "https://github.com/org/tp1-equipo")

        val modifiedFilesJson = (1..25).joinToString(",") { "\"src/Archivo$it.java\"" }
        val payloadInit = """
        {
          "repository": { "name": "tp1-equipo" },
          "commits": [
            {
              "id": "c_init",
              "message": "Initial commit",
              "modified": [$modifiedFilesJson],
              "additions": 1000
            }
          ]
        }
        """.trimIndent()

        val resultadoInit = service.analizarCommitsDePush(repo, objectMapper.readTree(payloadInit))
        assertEquals(0, resultadoInit.size)

        val payloadMerge = """
        {
          "repository": { "name": "tp1-equipo" },
          "commits": [
            {
              "id": "c_merge",
              "message": "Merge branch 'feature' into main",
              "merge": true,
              "modified": [$modifiedFilesJson],
              "additions": 1500
            }
          ]
        }
        """.trimIndent()

        val resultadoMerge = service.analizarCommitsDePush(repo, objectMapper.readTree(payloadMerge))
        assertEquals(0, resultadoMerge.size)
    }

    @Test
    fun `analizarCommitsDePush detecta mensajes vagos, cortos o sin Conventional Commits y sugiere el formato`() {
        val repo = Repositorio(id = 100L, nombre = "tp1-carlos", htmlUrl = "https://github.com/org/tp1-carlos")
        `when`(advertenciaGitRepository.save(anyAdvertencia())).thenAnswer { it.getArgument(0) }

        val payloadVago = """
        {
          "repository": { "name": "tp1-carlos" },
          "commits": [
            {
              "id": "c_vago_1",
              "message": "cambios",
              "modified": ["src/A.kt"]
            }
          ]
        }
        """.trimIndent()

        val resVago = service.analizarCommitsDePush(repo, objectMapper.readTree(payloadVago))
        assertEquals(1, resVago.size)
        assertEquals(TipoAdvertenciaGit.MENSAJE_INADECUADO, resVago[0].tipo)
        assertTrue(resVago[0].mensajeExplicativo.contains("Conventional Commits"))

        val payloadSinPrefijo = """
        {
          "repository": { "name": "tp1-carlos" },
          "commits": [
            {
              "id": "c_sin_prefijo",
              "message": "arregle el bug del login",
              "modified": ["src/Auth.kt"]
            }
          ]
        }
        """.trimIndent()

        val resSinPrefijo = service.analizarCommitsDePush(repo, objectMapper.readTree(payloadSinPrefijo))
        assertEquals(1, resSinPrefijo.size)
        assertEquals(TipoAdvertenciaGit.MENSAJE_INADECUADO, resSinPrefijo[0].tipo)

        val payloadCorrecto = """
        {
          "repository": { "name": "tp1-carlos" },
          "commits": [
            {
              "id": "c_ok",
              "message": "fix(auth): corregir validacion de credenciales",
              "modified": ["src/Auth.kt"]
            }
          ]
        }
        """.trimIndent()

        val resCorrecto = service.analizarCommitsDePush(repo, objectMapper.readTree(payloadCorrecto))
        assertEquals(0, resCorrecto.size)
    }

    @Test
    fun `analizarCommitsDePush genera ambas advertencias si un mismo commit es gigante y tiene mensaje vago`() {
        val repo = Repositorio(id = 100L, nombre = "tp1-mixto", htmlUrl = "https://github.com/org/tp1-mixto")
        `when`(advertenciaGitRepository.save(anyAdvertencia())).thenAnswer { it.getArgument(0) }

        val modifiedFilesJson = (1..16).joinToString(",") { "\"src/Mod$it.kt\"" }
        val payload = """
        {
          "repository": { "name": "tp1-mixto" },
          "commits": [
            {
              "id": "c_ambos",
              "message": "wip",
              "modified": [$modifiedFilesJson]
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
