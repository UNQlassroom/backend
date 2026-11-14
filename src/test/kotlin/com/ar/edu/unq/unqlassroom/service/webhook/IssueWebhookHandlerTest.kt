package com.ar.edu.unq.unqlassroom.service.webhook

import com.ar.edu.unq.unqlassroom.model.Issue
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.webhook.WebhookEvent
import com.ar.edu.unq.unqlassroom.repository.IssueRepository
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class IssueWebhookHandlerTest {

    @Mock
    private lateinit var repositorioRepository: RepositorioRepository

    @Mock
    private lateinit var issueRepository: IssueRepository

    private val objectMapper = ObjectMapper()
    private lateinit var handler: IssueWebhookHandler

    @BeforeEach
    fun setUp() {
        handler = IssueWebhookHandler(repositorioRepository, issueRepository, objectMapper)
    }

    @Test
    fun `canHandle retorna true solo para eventos issues e issue_comment case-insensitive`() {
        val eventIssues = WebhookEvent(eventType = "issues", payload = "{}")
        val eventIssuesUpper = WebhookEvent(eventType = "ISSUES", payload = "{}")
        val eventComment = WebhookEvent(eventType = "issue_comment", payload = "{}")
        val eventCommentMixed = WebhookEvent(eventType = "Issue_Comment", payload = "{}")
        val eventPush = WebhookEvent(eventType = "push", payload = "{}")
        val eventRelease = WebhookEvent(eventType = "release", payload = "{}")

        assertTrue(handler.canHandle(eventIssues))
        assertTrue(handler.canHandle(eventIssuesUpper))
        assertTrue(handler.canHandle(eventComment))
        assertTrue(handler.canHandle(eventCommentMixed))
        assertFalse(handler.canHandle(eventPush))
        assertFalse(handler.canHandle(eventRelease))
    }

    @Test
    fun `handle crea nuevo Issue cuando action es opened`() {
        val payload = """
        {
          "action": "opened",
          "repository": {
            "name": "2026s1_c1_bases_de_datos_prueba_10_thiagodeprueba"
          },
          "issue": {
            "number": 2,
            "title": "aca va el titulo del issue",
            "html_url": "https://github.com/UNQlassroom-cursos/2026s1_c1_bases_de_datos_prueba_10_thiagodeprueba/issues/2",
            "state": "open",
            "comments": 0,
            "created_at": "2026-10-20T12:46:34Z",
            "updated_at": "2026-10-20T12:46:34Z",
            "closed_at": null,
            "user": {
              "login": "thiagosofarelli"
            }
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "issues",
            action = "opened",
            payload = payload
        )

        val repo = Repositorio(
            id = 1L,
            nombre = "2026s1_c1_bases_de_datos_prueba_10_thiagodeprueba",
            htmlUrl = "https://github.com/UNQlassroom-cursos/2026s1_c1_bases_de_datos_prueba_10_thiagodeprueba"
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("2026s1_c1_bases_de_datos_prueba_10_thiagodeprueba"))
            .thenReturn(repo)
        `when`(issueRepository.findByRepositorioIdAndNumero(1L, 2)).thenReturn(null)

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(Issue::class.java)
        verify(issueRepository).save(captor.capture())

        val guardado = captor.value
        assertEquals(2, guardado.numero)
        assertEquals("aca va el titulo del issue", guardado.titulo)
        assertEquals("open", guardado.estadoGitHub)
        assertEquals(0, guardado.cantComentarios)
        assertEquals("thiagosofarelli", guardado.autor)
        assertEquals("2026-10-20T12:46:34Z", guardado.fechaCreacion)
        assertNull(guardado.fechaCierre)
        assertFalse(guardado.eliminado)
        assertEquals(repo, guardado.repositorio)
    }

    @Test
    fun `handle actualiza estado a closed y fechaCierre cuando action es closed`() {
        val payload = """
        {
          "action": "closed",
          "repository": {
            "name": "repo1"
          },
          "issue": {
            "number": 5,
            "title": "Issue a cerrar",
            "html_url": "https://github.com/org/repo1/issues/5",
            "state": "closed",
            "comments": 3,
            "created_at": "2026-10-18T10:00:00Z",
            "updated_at": "2026-10-20T12:00:00Z",
            "closed_at": "2026-10-20T12:00:00Z",
            "user": {
              "login": "alumno1"
            }
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "issues",
            action = "closed",
            payload = payload
        )

        val repo = Repositorio(id = 10L, nombre = "repo1", htmlUrl = "https://github.com/org/repo1")
        val issueExistente = Issue(
            id = 50L,
            numero = 5,
            titulo = "Issue previo",
            htmlUrl = "https://github.com/org/repo1/issues/5",
            autor = "alumno1",
            estadoGitHub = "open",
            cantComentarios = 2,
            fechaCreacion = "2026-10-18T10:00:00Z",
            fechaActualizacion = "2026-10-18T10:00:00Z",
            fechaCierre = null,
            eliminado = false,
            repositorio = repo
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("repo1")).thenReturn(repo)
        `when`(issueRepository.findByRepositorioIdAndNumero(10L, 5)).thenReturn(issueExistente)

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(Issue::class.java)
        verify(issueRepository).save(captor.capture())

        val guardado = captor.value
        assertEquals("closed", guardado.estadoGitHub)
        assertEquals(3, guardado.cantComentarios)
        assertEquals("2026-10-20T12:00:00Z", guardado.fechaCierre)
        assertFalse(guardado.eliminado)
    }

    @Test
    fun `handle marca eliminado true cuando action es deleted en evento issues`() {
        val payload = """
        {
          "action": "deleted",
          "repository": {
            "name": "repo1"
          },
          "issue": {
            "number": 7,
            "title": "Issue borrado",
            "state": "open"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "issues",
            action = "deleted",
            payload = payload
        )

        val repo = Repositorio(id = 10L, nombre = "repo1", htmlUrl = "https://github.com/org/repo1")
        val issueExistente = Issue(
            id = 70L,
            numero = 7,
            titulo = "Issue borrado",
            htmlUrl = "https://github.com/org/repo1/issues/7",
            autor = "profe",
            estadoGitHub = "open",
            cantComentarios = 0,
            fechaCreacion = "2026-10-18T10:00:00Z",
            fechaActualizacion = "2026-10-18T10:00:00Z",
            fechaCierre = null,
            eliminado = false,
            repositorio = repo
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("repo1")).thenReturn(repo)
        `when`(issueRepository.findByRepositorioIdAndNumero(10L, 7)).thenReturn(issueExistente)

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(Issue::class.java)
        verify(issueRepository).save(captor.capture())

        val guardado = captor.value
        assertTrue(guardado.eliminado)
    }

    @Test
    fun `handle actualiza cantComentarios cuando evento es issue_comment`() {
        val payload = """
        {
          "action": "created",
          "repository": {
            "name": "repo1"
          },
          "issue": {
            "number": 2,
            "title": "aca va el titulo del issue",
            "html_url": "https://github.com/org/repo1/issues/2",
            "state": "open",
            "comments": 4,
            "created_at": "2026-10-20T12:00:00Z",
            "updated_at": "2026-10-20T12:50:00Z",
            "closed_at": null,
            "user": {
              "login": "thiagosofarelli"
            }
          },
          "comment": {
            "id": 12345,
            "body": "nuevo comentario"
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(
            id = UUID.randomUUID(),
            eventType = "issue_comment",
            action = "created",
            payload = payload
        )

        val repo = Repositorio(id = 1L, nombre = "repo1", htmlUrl = "https://github.com/org/repo1")
        val issueExistente = Issue(
            id = 20L,
            numero = 2,
            titulo = "aca va el titulo del issue",
            htmlUrl = "https://github.com/org/repo1/issues/2",
            autor = "thiagosofarelli",
            estadoGitHub = "open",
            cantComentarios = 3,
            fechaCreacion = "2026-10-20T12:00:00Z",
            fechaActualizacion = "2026-10-20T12:00:00Z",
            fechaCierre = null,
            eliminado = false,
            repositorio = repo
        )

        `when`(repositorioRepository.findByNombreIgnoreCase("repo1")).thenReturn(repo)
        `when`(issueRepository.findByRepositorioIdAndNumero(1L, 2)).thenReturn(issueExistente)

        handler.handle(evento)

        val captor = ArgumentCaptor.forClass(Issue::class.java)
        verify(issueRepository).save(captor.capture())

        val guardado = captor.value
        assertEquals(4, guardado.cantComentarios)
        assertEquals("2026-10-20T12:50:00Z", guardado.fechaActualizacion)
    }

    @Test
    fun `handle ignora pull requests si viene pull_request en el payload`() {
        val payload = """
        {
          "action": "opened",
          "repository": {
            "name": "repo1"
          },
          "issue": {
            "number": 10,
            "title": "PR como issue",
            "pull_request": {
              "url": "https://api.github.com/repos/org/repo1/pulls/10"
            }
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(
            eventType = "issues",
            action = "opened",
            payload = payload
        )

        val repo = Repositorio(id = 1L, nombre = "repo1", htmlUrl = "https://github.com/org/repo1")
        `when`(repositorioRepository.findByNombreIgnoreCase("repo1")).thenReturn(repo)

        handler.handle(evento)

        verifyNoInteractions(issueRepository)
    }

    @Test
    fun `handle no realiza acciones cuando no se encuentra repositorio en BD`() {
        val payload = """
        {
          "action": "opened",
          "repository": {
            "name": "repo-inexistente"
          },
          "issue": {
            "number": 1
          }
        }
        """.trimIndent()

        val evento = WebhookEvent(eventType = "issues", payload = payload)
        `when`(repositorioRepository.findByNombreIgnoreCase("repo-inexistente")).thenReturn(null)

        handler.handle(evento)

        verifyNoInteractions(issueRepository)
    }
}
