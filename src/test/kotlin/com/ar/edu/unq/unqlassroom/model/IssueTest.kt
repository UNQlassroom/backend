package com.ar.edu.unq.unqlassroom.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class IssueTest {

    private fun crearRepo(fechaCommit: String? = "2026-10-01T12:00:00Z"): Repositorio {
        return Repositorio(
            id = 1L,
            nombre = "repo-test",
            htmlUrl = "https://github.com/org/repo-test",
            fechaUltimoCommit = fechaCommit
        )
    }

    private fun crearIssue(
        estadoGitHub: String = "open",
        cantComentarios: Int = 0,
        fechaCreacion: String = "2026-10-01T10:00:00Z",
        repo: Repositorio = crearRepo()
    ): Issue {
        return Issue(
            id = 10L,
            numero = 1,
            titulo = "Test Issue",
            htmlUrl = "https://github.com/org/repo-test/issues/1",
            autor = "docente",
            estadoGitHub = estadoGitHub,
            cantComentarios = cantComentarios,
            fechaCreacion = fechaCreacion,
            fechaActualizacion = fechaCreacion,
            fechaCierre = null,
            eliminado = false,
            repositorio = repo
        )
    }

    @Test
    fun `calcularEstado retorna RESUELTO cuando estadoGitHub es closed case-insensitive`() {
        val issue = crearIssue(estadoGitHub = "closed", cantComentarios = 5)
        assertEquals(EstadoIssue.RESUELTO, issue.calcularEstado())

        val issueUpper = crearIssue(estadoGitHub = "CLOSED", cantComentarios = 0)
        assertEquals(EstadoIssue.RESUELTO, issueUpper.calcularEstado())
    }

    @Test
    fun `calcularEstado retorna ACTUALIZADO cuando tiene commits posteriores a fechaCreacion`() {
        // Commit a las 12:00, Issue a las 10:00 del mismo día
        val repo = crearRepo(fechaCommit = "2026-10-01T12:00:00Z")
        val issue = crearIssue(
            estadoGitHub = "open",
            cantComentarios = 0,
            fechaCreacion = "2026-10-01T10:00:00Z",
            repo = repo
        )

        assertTrue(issue.tieneCommitsPosteriores())
        assertEquals(EstadoIssue.ACTUALIZADO, issue.calcularEstado())
    }

    @Test
    fun `calcularEstado retorna ACTUALIZADO cuando tiene comentarios mayor a cero aunque el commit sea anterior`() {
        // Commit a las 09:00, Issue a las 10:00
        val repo = crearRepo(fechaCommit = "2026-10-01T09:00:00Z")
        val issue = crearIssue(
            estadoGitHub = "open",
            cantComentarios = 2,
            fechaCreacion = "2026-10-01T10:00:00Z",
            repo = repo
        )

        assertFalse(issue.tieneCommitsPosteriores())
        assertEquals(EstadoIssue.ACTUALIZADO, issue.calcularEstado())
    }

    @Test
    fun `calcularEstado retorna PENDIENTE cuando estado es open, 0 comentarios y sin commits posteriores`() {
        val repo = crearRepo(fechaCommit = "2026-10-01T08:00:00Z")
        val issue = crearIssue(
            estadoGitHub = "open",
            cantComentarios = 0,
            fechaCreacion = "2026-10-01T10:00:00Z",
            repo = repo
        )

        assertFalse(issue.tieneCommitsPosteriores())
        assertEquals(EstadoIssue.PENDIENTE, issue.calcularEstado())
    }

    @Test
    fun `tieneCommitsPosteriores retorna false cuando fechaCommit es null o vacia`() {
        val repo = crearRepo(fechaCommit = null)
        val issue = crearIssue(repo = repo)

        assertFalse(issue.tieneCommitsPosteriores())
        assertFalse(issue.tieneCommitsPosteriores(null))
        assertFalse(issue.tieneCommitsPosteriores("   "))
    }

    @Test
    fun `tieneCommitsPosteriores retorna false cuando el formato de fecha es invalido`() {
        val issue = crearIssue()
        assertFalse(issue.tieneCommitsPosteriores("fecha-invalida"))
    }

    @Test
    fun `actualizarDesdeWebhook actualiza datos y reactiva si estaba eliminado`() {
        val issue = crearIssue()
        issue.marcarComoEliminado()
        assertTrue(issue.eliminado)

        issue.actualizarDesdeWebhook(
            titulo = "Nuevo Titulo",
            estadoGitHub = "closed",
            cantComentarios = 4,
            fechaActualizacion = "2026-10-02T15:00:00Z",
            fechaCierre = "2026-10-02T15:00:00Z"
        )

        assertEquals("Nuevo Titulo", issue.titulo)
        assertEquals("closed", issue.estadoGitHub)
        assertEquals(4, issue.cantComentarios)
        assertEquals("2026-10-02T15:00:00Z", issue.fechaActualizacion)
        assertEquals("2026-10-02T15:00:00Z", issue.fechaCierre)
        assertFalse(issue.eliminado)
    }

    @Test
    fun `marcarComoEliminado setea eliminado en true`() {
        val issue = crearIssue()
        assertFalse(issue.eliminado)

        issue.marcarComoEliminado()
        assertTrue(issue.eliminado)
    }
}
