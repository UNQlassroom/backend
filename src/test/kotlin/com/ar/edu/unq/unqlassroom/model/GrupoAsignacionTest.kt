package com.ar.edu.unq.unqlassroom.model

import org.junit.jupiter.api.Assertions
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

    @Test
    fun `calificar sets calificacion, observaciones and fechaCalificacion when nota is between 1 and 10`() {
        val grupo = GrupoAsignacion(id = 1L)
        grupo.calificar(8, "Excelente")
        assertEquals(8, grupo.calificacion)
        assertEquals("Excelente", grupo.observaciones)
        Assertions.assertNotNull(grupo.fechaCalificacion)
    }

    @Test
    fun `calificar throws BadRequestException when nota is out of bounds`() {
        val grupo = GrupoAsignacion(id = 1L)
        val ex1 = org.junit.jupiter.api.assertThrows<com.ar.edu.unq.unqlassroom.exception.BadRequestException> {
            grupo.calificar(0, null)
        }
        assertEquals("La nota debe ser entre 1 y 10", ex1.message)

        val ex2 = org.junit.jupiter.api.assertThrows<com.ar.edu.unq.unqlassroom.exception.BadRequestException> {
            grupo.calificar(11, null)
        }
        assertEquals("La nota debe ser entre 1 y 10", ex2.message)
    }

    @Test
    fun `registrarEntrega updates cantidadEntregas, entregada, fechaEntregada and releaseUrl`() {
        val grupo = GrupoAsignacion(id = 1L)
        assertEquals(0, grupo.cantidadEntregas)
        Assertions.assertFalse(grupo.entregada)

        grupo.registrarEntrega("https://github.com/rel/1")
        assertEquals(1, grupo.cantidadEntregas)
        assertTrue(grupo.entregada)
        Assertions.assertNotNull(grupo.fechaEntregada)
        assertEquals("https://github.com/rel/1", grupo.releaseUrl)
    }

    @Test
    fun `tieneIntegrante returns true when user is present regardless of casing`() {
        val alumno = Usuario(username = "JuanPerez")
        val grupo = GrupoAsignacion(integrantes = mutableListOf(alumno))
        assertTrue(grupo.tieneIntegrante("juanperez"))
        assertTrue(grupo.tieneIntegrante(" JuanPerez "))
        Assertions.assertFalse(grupo.tieneIntegrante("otro"))
    }

    @Test
    fun `release generators create correct tag, name and body`() {
        val grupo = GrupoAsignacion(cantidadEntregas = 2)
        assertEquals("entrega-v3", grupo.generarProximoTagNameRelease())
        assertEquals("Entrega v2 - TP Final", grupo.generarNombreRelease("TP Final"))
        assertTrue(grupo.generarCuerpoRelease("profe").contains("profe"))
    }
}

