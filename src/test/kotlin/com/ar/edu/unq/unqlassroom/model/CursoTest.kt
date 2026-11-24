package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class CursoTest {

    @Test
    fun `esDocente returns true for assigned docentes and false otherwise`() {
        val docente1 = Usuario(id = 1L, username = "profe1")
        val docente2 = Usuario(id = 2L, username = "profe2")
        val curso = Curso(
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docente1, docente2)
        )

        assertTrue(curso.esDocente("profe1"))
        assertTrue(curso.esDocente("profe2"))
        assertTrue(curso.esDocente("PROFE1"))
        assertFalse(curso.esDocente("otro"))
        assertFalse(curso.esDocente(null))
    }

    @Test
    fun `curso creation validations pass when valid`() {
        val docente1 = Usuario(id = 1L, username = "profe1")
        val curso = Curso(
            materia = "  Estructuras de Datos  ",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docente1)
        )

        assertEquals("Estructuras de Datos", curso.materia)
    }

    @Test
    fun `curso creation throws exceptions for invalid data`() {
        assertThrows<NombreMateriaVacioException> {
            Curso(materia = "   ", anio = 2026, semestre = 1, comision = 1)
        }
        assertThrows<AnioCursoInvalidoException> {
            Curso(materia = "Estructuras", anio = 1999, semestre = 1, comision = 1)
        }
        assertThrows<SemestreInvalidoException> {
            Curso(materia = "Estructuras", anio = 2026, semestre = 3, comision = 1)
        }
        assertThrows<ComisionInvalidaException> {
            Curso(materia = "Estructuras", anio = 2026, semestre = 1, comision = 0)
        }
    }

    @Test
    fun `agregarDocente agrega exitosamente`() {
        val docente1 = Usuario(id = 1L, username = "profe1")
        val usuario = Usuario(id = 2L, username = "alumno_o_ayudante")
        val curso = Curso(
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docente1)
        )

        curso.agregarDocente(usuario)

        assertTrue(curso.esDocente("alumno_o_ayudante"))
        assertEquals(2, curso.docentes.size)
    }

    @Test
    fun `agregarDocente throws DocenteDuplicadoException when already in course`() {
        val docente1 = Usuario(id = 1L, username = "profe1")
        val curso = Curso(
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docente1)
        )

        assertThrows<DocenteDuplicadoException> {
            curso.agregarDocente(docente1)
        }
    }

    @Test
    fun `removerDocente removes docente successfully`() {
        val docente1 = Usuario(id = 1L, username = "profe1")
        val docente2 = Usuario(id = 2L, username = "profe2")
        val curso = Curso(
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docente1, docente2)
        )

        curso.removerDocente("profe1")

        assertFalse(curso.esDocente("profe1"))
        assertTrue(curso.esDocente("profe2"))
        assertEquals(1, curso.docentes.size)
    }

    @Test
    fun `removerDocente throws CursoSinDocentesException when trying to remove only docente`() {
        val docente1 = Usuario(id = 1L, username = "profe1")
        val curso = Curso(
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docente1)
        )

        assertThrows<CursoSinDocentesException> {
            curso.removerDocente("profe1")
        }
    }

    @Test
    fun `removerDocente throws DocenteNoAsignadoException when docente not in course`() {
        val docente1 = Usuario(id = 1L, username = "profe1")
        val curso = Curso(
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            docentes = mutableListOf(docente1)
        )

        assertThrows<DocenteNoAsignadoException> {
            curso.removerDocente("fantasma")
        }
    }

    @Test
    fun `archivar sets archivado true`() {
        val curso = Curso(
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1
        )
        assertFalse(curso.archivado)
        curso.archivar()
        assertTrue(curso.archivado)
    }

    @Test
    fun `archivar throws CursoArchivadoException when already archived`() {
        val curso = Curso(
            materia = "Estructuras",
            anio = 2026,
            semestre = 1,
            comision = 1,
            archivado = true
        )
        assertThrows<CursoArchivadoException> {
            curso.archivar()
        }
    }

    @Test
    fun `fechaFinSemestre returns expected end dates`() {
        val cursoS1 = Curso(materia = "Estructuras", anio = 2026, semestre = 1, comision = 1)
        val cursoS2 = Curso(materia = "Estructuras", anio = 2026, semestre = 2, comision = 1)

        assertEquals(LocalDate.of(2026, 7, 31), cursoS1.fechaFinSemestre())
        assertEquals(LocalDate.of(2026, 12, 31), cursoS2.fechaFinSemestre())
    }

    @Test
    fun `debeArchivarseAutomaticamente returns true after 2 months of semester end`() {
        val curso = Curso(materia = "Estructuras", anio = 2026, semestre = 1, comision = 1)
        val fechaAntes = LocalDate.of(2026, 9, 29)
        val fechaExacta = LocalDate.of(2026, 9, 30)

        assertFalse(curso.debeArchivarseAutomaticamente(fechaAntes))
        assertTrue(curso.debeArchivarseAutomaticamente(fechaExacta))
    }

    @Test
    fun `debeArchivarseAutomaticamente returns false when already archived`() {
        val curso = Curso(materia = "Estructuras", anio = 2026, semestre = 1, comision = 1, archivado = true)
        val fechaDespues = LocalDate.of(2026, 11, 1)

        assertFalse(curso.debeArchivarseAutomaticamente(fechaDespues))
    }

    @Test
    fun `obtenerNombresRepositorios collects unique repos from asignaciones`() {
        val curso = Curso(materia = "Estructuras", anio = 2026, semestre = 1, comision = 1)
        val asignacion = Asignacion(
            titulo = "TP1",
            templateRepoName = "template",
            tipo = TipoAsignacion.INDIVIDUAL,
            curso = curso
        )
        val repo = Repositorio(nombre = "repo-alumno1", htmlUrl = "url1")
        val grupo = GrupoAsignacion(nombre = "grupo1", asignacion = asignacion, repositorio = repo)
        asignacion.grupos = mutableListOf(grupo)
        curso.asignaciones = mutableListOf(asignacion)

        val repos = curso.obtenerNombresRepositorios()
        assertEquals(listOf("repo-alumno1"), repos)
    }

    @Test
    fun `generarNombreArchivoExportacion formats correctly`() {
        val curso = Curso(materia = "Estructuras de Datos", anio = 2026, semestre = 1, comision = 1)
        assertEquals("curso_estructuras_de_datos_2026_c1_unqlassroom.xlsx", curso.generarNombreArchivoExportacion())
    }
}
