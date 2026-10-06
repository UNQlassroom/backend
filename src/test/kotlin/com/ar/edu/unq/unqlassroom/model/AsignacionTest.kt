package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.exception.BadRequestException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class AsignacionTest {

    private fun cursoDummy(): Curso =
        Curso(materia = "Base de Datos", anio = 2026, semestre = 1, comision = 1)

    @Test
    fun `estaVencida and validarNoVencida work according to fechaLimite`() {
        val futuro = LocalDateTime.now().plusDays(5)
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            fechaLimite = futuro,
            curso = cursoDummy()
        )

        assertFalse(asignacion.estaVencida())
        assertDoesNotThrow { asignacion.validarVencimiento() }

        val pasado = LocalDateTime.now().minusDays(1)
        val asignacionVencida = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            fechaLimite = pasado,
            curso = cursoDummy()
        )

        assertTrue(asignacionVencida.estaVencida())
        val ex = assertThrows<BadRequestException> {
            asignacionVencida.validarVencimiento()
        }
        assertEquals("No se puede entregar la asignación porque la fecha límite ha vencido", ex.message)
    }

    @Test
    fun `validarEstructuraGrupos INDIVIDUAL throws BadRequestException if grupos is not empty`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = cursoDummy()
        )
        asignacion.grupos.add(GrupoAsignacion(nombre = "Grupo 1"))

        val ex = assertThrows<BadRequestException> {
            asignacion.validarEstructuraGrupos()
        }
        assertEquals("No se pueden especificar grupos para una asignación individual", ex.message)
    }

    @Test
    fun `validarEstructuraGrupos GRUPAL throws BadRequestException on missing or empty groups`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl",
            curso = cursoDummy()
        )

        val ex1 = assertThrows<BadRequestException> {
            asignacion.validarEstructuraGrupos()
        }
        assertEquals("Para una asignación grupal debe especificar al menos un grupo", ex1.message)

        asignacion.grupos.add(GrupoAsignacion(nombre = "Grupo Vacío", integrantes = mutableListOf()))
        val ex2 = assertThrows<BadRequestException> {
            asignacion.validarEstructuraGrupos()
        }
        assertEquals("Todos los grupos deben tener al menos un integrante", ex2.message)
    }

    @Test
    fun `validarEstructuraGrupos GRUPAL throws BadRequestException on blank group name`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl",
            curso = cursoDummy()
        )
        asignacion.grupos.add(GrupoAsignacion(nombre = "   ", integrantes = mutableListOf(Usuario(username = "u1"))))

        val ex = assertThrows<BadRequestException> {
            asignacion.validarEstructuraGrupos()
        }
        assertEquals("El nombre del grupo no puede estar vacío", ex.message)
    }

    @Test
    fun `validarEstructuraGrupos GRUPAL throws BadRequestException on duplicate names or duplicate students`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl",
            curso = cursoDummy()
        )
        val u1 = Usuario(username = "u1")
        val u2 = Usuario(username = "u2")
        asignacion.grupos.add(GrupoAsignacion(nombre = "Alfa", integrantes = mutableListOf(u1)))
        asignacion.grupos.add(GrupoAsignacion(nombre = "alfa", integrantes = mutableListOf(u2)))

        val ex1 = assertThrows<BadRequestException> {
            asignacion.validarEstructuraGrupos()
        }
        assertEquals("No puede haber grupos con el mismo nombre en la misma asignación", ex1.message)

        asignacion.grupos.clear()
        asignacion.grupos.add(GrupoAsignacion(nombre = "Alfa", integrantes = mutableListOf(u1)))
        asignacion.grupos.add(GrupoAsignacion(nombre = "Beta", integrantes = mutableListOf(u1)))

        val ex2 = assertThrows<BadRequestException> {
            asignacion.validarEstructuraGrupos()
        }
        assertEquals("Un alumno no puede pertenecer a más de un grupo en la misma asignación", ex2.message)
    }

    @Test
    fun `buscarGrupo and buscarGrupoPorAlumno return the correct group or throw`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl",
            curso = cursoDummy()
        )
        val u1 = Usuario(username = "alumno1")
        val g1 = GrupoAsignacion(id = 10L, nombre = "Alfa", integrantes = mutableListOf(u1))
        asignacion.grupos.add(g1)

        assertEquals(g1, asignacion.buscarGrupo(10L))
        assertEquals(g1, asignacion.buscarGrupoPorAlumno("alumno1"))

        assertThrows<BadRequestException> {
            asignacion.buscarGrupo(99L)
        }
        assertThrows<BadRequestException> {
            asignacion.buscarGrupoPorAlumno("desconocido")
        }
    }

    @Test
    fun `paraVisualizacionDe filters groups for students and preserves all for owner`() {
        val asignacion = Asignacion(
            id = 1L,
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl",
            curso = cursoDummy()
        )
        val u1 = Usuario(username = "alumno1")
        val u2 = Usuario(username = "alumno2")
        val g1 = GrupoAsignacion(id = 10L, nombre = "Alfa", integrantes = mutableListOf(u1))
        val g2 = GrupoAsignacion(id = 20L, nombre = "Beta", integrantes = mutableListOf(u2))
        asignacion.grupos.addAll(listOf(g1, g2))

        val paraOwner = asignacion.paraVisualizacionDe("profe", esOwner = true)
        assertEquals(2, paraOwner.grupos.size)

        val paraAlumno = asignacion.paraVisualizacionDe("alumno1", esOwner = false)
        assertEquals(1, paraAlumno.grupos.size)
        assertEquals("Alfa", paraAlumno.grupos[0].nombre)
    }
}
