package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class CursoTest {

    @Test
    fun `esOwner returns true only when owner matches and is docente`() {
        val docente = Usuario(id = 1L, username = "profe_titular", esDocente = true)
        val curso = Curso(materia = "Estructuras", anio = 2026, semestre = 1, comision = 1, owner = docente)

        assertTrue(curso.esOwner("profe_titular"))
        assertFalse(curso.esOwner("otro"))
        assertFalse(curso.esOwner(null))
    }

    @Test
    fun `esOwner returns false when owner is not docente`() {
        val noDocente = Usuario(id = 2L, username = "alumno", esDocente = false)
        val curso = Curso(materia = "Estructuras", anio = 2026, semestre = 1, comision = 1, owner = noDocente)

        assertFalse(curso.esOwner("alumno"))
    }

    @Test
    fun `asignarOwner sets owner when user is docente and throws when not`() {
        val docente = Usuario(id = 1L, username = "profe_titular", esDocente = true)
        val noDocente = Usuario(id = 2L, username = "alumno", esDocente = false)
        val curso = Curso(materia = "Estructuras", anio = 2026, semestre = 1, comision = 1)

        curso.asignarOwner(docente)
        assertEquals(docente, curso.owner)

        val ex = assertThrows<UsuarioNoEsDocenteException> {
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
        assertThrows<NombreMateriaVacioException> {
            Curso(materia = "", anio = 2026, semestre = 1, comision = 1)
        }
        assertThrows<AnioCursoInvalidoException> {
            Curso(materia = "Redes", anio = 1999, semestre = 1, comision = 1)
        }
        assertThrows<SemestreInvalidoException> {
            Curso(materia = "Redes", anio = 2026, semestre = 3, comision = 1)
        }
        assertThrows<ComisionInvalidaException> {
            Curso(materia = "Redes", anio = 2026, semestre = 1, comision = 0)
        }
    }

    @Test
    fun `archivar marks curso as archivado and throws CursoArchivadoException if already archived`() {
        val curso = Curso(materia = "Redes", anio = 2026, semestre = 1, comision = 1)
        assertFalse(curso.archivado)

        curso.archivar()
        assertTrue(curso.archivado)

        assertThrows<CursoArchivadoException> {
            curso.archivar()
        }
        assertThrows<CursoArchivadoException> {
            curso.validarNoArchivado()
        }
    }

    @Test
    fun `fechaFinSemestre and debeArchivarseAutomaticamente respect July 31 and Dec 31 plus 2 months`() {
        val cursoSem1 = Curso(materia = "Algoritmos", anio = 2026, semestre = 1, comision = 1)
        assertEquals(LocalDate.of(2026, 7, 31), cursoSem1.fechaFinSemestre())
        assertFalse(cursoSem1.debeArchivarseAutomaticamente(LocalDate.of(2026, 9, 29)))
        assertTrue(cursoSem1.debeArchivarseAutomaticamente(LocalDate.of(2026, 9, 30)))

        val cursoSem2 = Curso(materia = "Algoritmos", anio = 2026, semestre = 2, comision = 1)
        assertEquals(LocalDate.of(2026, 12, 31), cursoSem2.fechaFinSemestre())
        assertFalse(cursoSem2.debeArchivarseAutomaticamente(LocalDate.of(2027, 2, 27)))
        assertTrue(cursoSem2.debeArchivarseAutomaticamente(LocalDate.of(2027, 2, 28)))
    }
}
