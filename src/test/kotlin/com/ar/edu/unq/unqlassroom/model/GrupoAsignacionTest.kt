package com.ar.edu.unq.unqlassroom.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class GrupoAsignacionTest {

    @Test
    fun `normalizarNombre converts accented characters to lower case with underscores`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl",
            curso = Curso(materia = "BD", anio = 2026, semestre = 2, comision = 1)
        )
        val repo = Repositorio(nombre = "repo-grupo", htmlUrl = "https://github.com/repo-grupo")

        val grupo = GrupoAsignacion(
            nombre = "  Grupo Álgebra & Métodos Avanzados  ",
            asignacion = asignacion,
            repositorio = repo
        )

        assertEquals("grupo_algebra_&_metodos_avanzados", grupo.normalizarNombre())
    }

    @Test
    fun `normalizarNombre returns empty string when nombre is null`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = Curso(materia = "BD", anio = 2026, semestre = 2, comision = 1)
        )
        val repo = Repositorio(nombre = "repo-grupo", htmlUrl = "https://github.com/repo-grupo")

        val grupo = GrupoAsignacion(
            nombre = null,
            asignacion = asignacion,
            repositorio = repo
        )

        assertEquals("", grupo.normalizarNombre())
    }

    @Test
    fun `grupoAsignacion properties can be set and read`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl",
            curso = Curso(materia = "BD", anio = 2026, semestre = 2, comision = 1)
        )
        val repo = Repositorio(nombre = "repo-grupo", htmlUrl = "https://github.com/repo-grupo")
        val alumno = Usuario(id = 1L, username = "alumno1", esDocente = false)

        val ahora = LocalDateTime.now()
        val grupo = GrupoAsignacion(
            id = 50L,
            nombre = "Grupo 1",
            asignacion = asignacion,
            integrantes = mutableListOf(alumno),
            repositorio = repo,
            entregada = true,
            fechaEntregada = ahora,
            releaseUrl = "https://release.url",
            cantidadEntregas = 2,
            calificacion = 9,
            observaciones = "Muy buen trabajo",
            fechaCalificacion = ahora
        )

        assertEquals(50L, grupo.id)
        assertEquals("Grupo 1", grupo.nombre)
        assertEquals(1, grupo.integrantes.size)
        assertTrue(grupo.entregada)
        assertEquals(2, grupo.cantidadEntregas)
        assertEquals(9, grupo.calificacion)
        assertEquals("Muy buen trabajo", grupo.observaciones)
        assertEquals(ahora, grupo.fechaCalificacion)
        assertEquals(ahora, grupo.fechaEntregada)
        assertEquals("https://release.url", grupo.releaseUrl)
    }
}
