package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.exception.ForbiddenException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CursoTest {

    @Test
    fun `esOwner returns true only when owner matches`() {
        val docente = Usuario(id = 1L, username = "profe_titular", esDocente = true)
        val curso = Curso(materia = "Estructuras", anio = 2026, semestre = 1, comision = 1, owner = docente)

        assertTrue(curso.esOwner("profe_titular"))
        assertFalse(curso.esOwner("otro"))
        assertFalse(curso.esOwner(null))
    }

    @Test
    fun `asignarOwner sets owner when user is docente and throws when not`() {
        val docente = Usuario(id = 1L, username = "profe_titular", esDocente = true)
        val noDocente = Usuario(id = 2L, username = "alumno", esDocente = false)
        val curso = Curso(materia = "Estructuras", anio = 2026, semestre = 1, comision = 1)

        curso.asignarOwner(docente)
        assertEquals(docente, curso.owner)

        val ex = assertThrows<ForbiddenException> {
            curso.asignarOwner(noDocente)
        }
        assertEquals("El usuario alumno no tiene permisos de docente", ex.message)
    }

    @Test
    fun `generarDescripcion builds expected string`() {
        val curso = Curso(materia = "Redes", anio = 2026, semestre = 2, comision = 3)
        assertEquals("Curso de Redes - Año 2026 - Semestre 2 - Comisión 3", curso.generarDescripcion())
    }

    @Test
    fun `init validates invariants`() {
        assertThrows<IllegalArgumentException> {
            Curso(materia = "", anio = 2026, semestre = 1, comision = 1)
        }
        assertThrows<IllegalArgumentException> {
            Curso(materia = "Redes", anio = 1999, semestre = 1, comision = 1)
        }
        assertThrows<IllegalArgumentException> {
            Curso(materia = "Redes", anio = 2026, semestre = 3, comision = 1)
        }
        assertThrows<IllegalArgumentException> {
            Curso(materia = "Redes", anio = 2026, semestre = 1, comision = 0)
        }
    }
}
