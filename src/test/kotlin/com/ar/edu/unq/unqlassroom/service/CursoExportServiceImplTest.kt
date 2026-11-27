package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.model.*
import com.ar.edu.unq.unqlassroom.service.impl.CursoExportServiceImpl
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class CursoExportServiceImplTest {

    private lateinit var cursoExportService: CursoExportService

    @BeforeEach
    fun setUp() {
        cursoExportService = CursoExportServiceImpl()
    }

    @Test
    fun `exportarPlanillaCurso creates valid excel file with headers and correct filename for empty course`() {
        val curso = Curso(
            id = 1L,
            materia = "Álgebra Lineal & Geometría",
            anio = 2026,
            semestre = 1,
            comision = 2
        )

        val archivo = cursoExportService.exportarPlanillaCurso(curso)

        assertEquals("curso_algebra_lineal_geometria_2026_c2_unqlassroom.xlsx", archivo.nombreArchivo)
        assertNotNull(archivo.contenido)
        assertTrue(archivo.contenido.isNotEmpty())

        val workbook = WorkbookFactory.create(ByteArrayInputStream(archivo.contenido))
        val sheet = workbook.getSheet("Calificaciones")
        assertNotNull(sheet)

        val headerRow = sheet.getRow(0)
        assertNotNull(headerRow)
        assertEquals("DNI", headerRow.getCell(0).stringCellValue)
        assertEquals("Apellido y Nombre", headerRow.getCell(1).stringCellValue)
        assertEquals("Usuario GitHub", headerRow.getCell(2).stringCellValue)
        assertEquals("Email", headerRow.getCell(3).stringCellValue)
        assertEquals("Estado", headerRow.getCell(4).stringCellValue)
        assertEquals("Promedio", headerRow.getCell(5).stringCellValue)

        // No students enrolled
        assertNull(sheet.getRow(1))
        workbook.close()
    }

    @Test
    fun `exportarPlanillaCurso correctly formats student rows, assignments, status and average`() {
        val curso = Curso(
            id = 10L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 2,
            comision = 1
        )

        val alumno1 = Usuario(
            id = 1L,
            username = "jperez",
            nombreCompleto = "Pérez, Juan",
            dni = "12345678",
            email = "juan.perez@unq.edu.ar"
        )
        val alumno2 = Usuario(
            id = 2L,
            username = "agomez",
            nombreCompleto = "Gómez, Ana",
            dni = null,
            email = null
        )
        val alumno3 = Usuario(
            id = 3L,
            username = "bzarate",
            nombreCompleto = null,
            dni = "87654321",
            email = "bzarate@unq.edu.ar"
        )

        val inscripcion1 = Inscripcion(id = 1L, curso = curso, usuario = alumno1, activa = true)
        val inscripcion2 = Inscripcion(id = 2L, curso = curso, usuario = alumno2, activa = true)
        val inscripcion3 = Inscripcion(id = 3L, curso = curso, usuario = alumno3, activa = false)

        curso.inscripciones.addAll(listOf(inscripcion1, inscripcion2, inscripcion3))

        val asignacion1 = Asignacion(
            id = 101L,
            titulo = "TP 1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tp1-template",
            curso = curso
        )
        val asignacion2 = Asignacion(
            id = 102L,
            titulo = "TP 2 Grupal",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tp2-template",
            curso = curso
        )

        // Alumno 1: TP 1 nota 8, TP 2 nota 10 -> Promedio 9.00
        val grupoA1Tp1 = GrupoAsignacion(id = 1L, asignacion = asignacion1, integrantes = mutableListOf(alumno1)).apply {
            calificar(8, "Muy buen trabajo")
        }
        val grupoA1Tp2 = GrupoAsignacion(id = 2L, asignacion = asignacion2, integrantes = mutableListOf(alumno1, alumno3)).apply {
            calificar(10, "Excelente")
        }

        // Alumno 2: TP 1 entregado sin calificar, TP 2 no entregado -> Promedio "-"
        val grupoA2Tp1 = GrupoAsignacion(id = 3L, asignacion = asignacion1, integrantes = mutableListOf(alumno2)).apply {
            registrarEntrega()
        }

        // Alumno 3: TP 1 no entregado, TP 2 en grupo con Alumno 1 (nota 10) -> Promedio 10.00
        asignacion1.grupos.addAll(listOf(grupoA1Tp1, grupoA2Tp1))
        asignacion2.grupos.add(grupoA1Tp2)

        curso.asignaciones.addAll(listOf(asignacion1, asignacion2))

        val archivo = cursoExportService.exportarPlanillaCurso(curso)
        val workbook = WorkbookFactory.create(ByteArrayInputStream(archivo.contenido))
        val sheet = workbook.getSheet("Calificaciones")

        // Encabezados
        val headerRow = sheet.getRow(0)
        assertEquals("DNI", headerRow.getCell(0).stringCellValue)
        assertEquals("Apellido y Nombre", headerRow.getCell(1).stringCellValue)
        assertEquals("Usuario GitHub", headerRow.getCell(2).stringCellValue)
        assertEquals("Email", headerRow.getCell(3).stringCellValue)
        assertEquals("Estado", headerRow.getCell(4).stringCellValue)
        assertEquals("TP 1", headerRow.getCell(5).stringCellValue)
        assertEquals("TP 2 Grupal", headerRow.getCell(6).stringCellValue)
        assertEquals("Promedio", headerRow.getCell(7).stringCellValue)

        // Orden alfabético por Apellido y Nombre:
        // 1. bzarate (nombreCompleto is null, fallback username "bzarate")
        // 2. Gómez, Ana
        // 3. Pérez, Juan

        // Row 1 (bzarate - Dado de baja):
        val row1 = sheet.getRow(1)
        assertEquals("87654321", row1.getCell(0).stringCellValue)
        assertEquals("bzarate", row1.getCell(1).stringCellValue)
        assertEquals("bzarate", row1.getCell(2).stringCellValue)
        assertEquals("bzarate@unq.edu.ar", row1.getCell(3).stringCellValue)
        assertEquals("Dado de baja", row1.getCell(4).stringCellValue)
        val styleBaja = row1.getCell(4).cellStyle as XSSFCellStyle
        assertEquals(FillPatternType.SOLID_FOREGROUND, styleBaja.fillPattern)
        assertTrue(styleBaja.fillForegroundXSSFColor?.argbHex?.contains("F8D7DA", ignoreCase = true) == true)

        assertEquals("No entregado", row1.getCell(5).stringCellValue)
        val styleNoEntregado = row1.getCell(5).cellStyle as XSSFCellStyle
        assertTrue(styleNoEntregado.fillForegroundXSSFColor?.argbHex?.contains("E9ECEF", ignoreCase = true) == true)

        assertEquals(10.0, row1.getCell(6).numericCellValue)
        assertEquals(10.0, row1.getCell(7).numericCellValue)

        // Row 2 (Gómez, Ana - Activo):
        val row2 = sheet.getRow(2)
        assertEquals("-", row2.getCell(0).stringCellValue)
        assertEquals("Gómez, Ana", row2.getCell(1).stringCellValue)
        assertEquals("agomez", row2.getCell(2).stringCellValue)
        assertEquals("-", row2.getCell(3).stringCellValue)
        assertEquals("Activo", row2.getCell(4).stringCellValue)
        val styleActivo = row2.getCell(4).cellStyle as XSSFCellStyle
        assertEquals(FillPatternType.SOLID_FOREGROUND, styleActivo.fillPattern)
        assertTrue(styleActivo.fillForegroundXSSFColor?.argbHex?.contains("D4EDDA", ignoreCase = true) == true)

        assertEquals("Sin calificar", row2.getCell(5).stringCellValue)
        val styleSinCalificar = row2.getCell(5).cellStyle as XSSFCellStyle
        assertTrue(styleSinCalificar.fillForegroundXSSFColor?.argbHex?.contains("FFF3CD", ignoreCase = true) == true)

        assertEquals("No entregado", row2.getCell(6).stringCellValue)
        assertEquals(CellType.STRING, row2.getCell(7).cellType)
        assertEquals("-", row2.getCell(7).stringCellValue)

        // Row 3 (Pérez, Juan - Activo):
        val row3 = sheet.getRow(3)
        assertEquals("12345678", row3.getCell(0).stringCellValue)
        assertEquals("Pérez, Juan", row3.getCell(1).stringCellValue)
        assertEquals("jperez", row3.getCell(2).stringCellValue)
        assertEquals("juan.perez@unq.edu.ar", row3.getCell(3).stringCellValue)
        assertEquals("Activo", row3.getCell(4).stringCellValue)
        assertEquals(8.0, row3.getCell(5).numericCellValue)
        assertEquals(10.0, row3.getCell(6).numericCellValue)
        assertEquals(9.0, row3.getCell(7).numericCellValue)

        workbook.close()
    }
}
