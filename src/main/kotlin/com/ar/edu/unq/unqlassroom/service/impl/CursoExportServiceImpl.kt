package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.dto.curso.response.ArchivoExportadoDTO
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.service.CursoExportService
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFFont
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream

@Service
class CursoExportServiceImpl : CursoExportService {

    companion object {
        // Colores en tonos pastel suaves con alto contraste para texto
        private val COLOR_ESTADO_ACTIVO_BG = Triple(212, 237, 218)      // Verde menta suave (#D4EDDA)
        private val COLOR_ESTADO_ACTIVO_TEXT = Triple(21, 87, 36)        // Verde oscuro (#155724)
        private val COLOR_ESTADO_BAJA_BG = Triple(248, 215, 218)        // Rojo pastel / coral (#F8D7DA)
        private val COLOR_ESTADO_BAJA_TEXT = Triple(114, 28, 36)        // Rojo oscuro (#721C24)

        private val COLOR_SIN_CALIFICAR_BG = Triple(255, 243, 205)      // Amarillo suave (#FFF3CD)
        private val COLOR_SIN_CALIFICAR_TEXT = Triple(133, 100, 4)      // Ámbar oscuro (#856404)
        private val COLOR_NO_ENTREGADO_BG = Triple(233, 236, 239)       // Gris claro (#E9ECEF)
        private val COLOR_NO_ENTREGADO_TEXT = Triple(108, 117, 125)     // Gris oscuro (#6C757D)

        private val COLOR_TEXTO_MUTED = Triple(140, 140, 140)           // Gris suave para atenuar filas dadas de baja
    }

    override fun exportarPlanillaCurso(curso: Curso): ArchivoExportadoDTO {
        val workbook: Workbook = XSSFWorkbook()
        val sheet: Sheet = workbook.createSheet("Calificaciones")

        val headerStyle = crearEstiloEncabezado(workbook)

        // Estilos para alumnos activos
        val dataStyleLeft = crearEstiloDato(workbook, HorizontalAlignment.LEFT)
        val dataStyleCenter = crearEstiloDato(workbook, HorizontalAlignment.CENTER)
        val integerFormat = workbook.createDataFormat().getFormat("0")
        val gradeStyle = workbook.createCellStyle().apply {
            cloneStyleFrom(dataStyleCenter)
            dataFormat = integerFormat
        }
        val decimalFormat = workbook.createDataFormat().getFormat("0.00")
        val averageStyle = workbook.createCellStyle().apply {
            cloneStyleFrom(dataStyleCenter)
            dataFormat = decimalFormat
        }

        // Estilos para alumnos dados de baja (fila atenuada)
        val dataStyleLeftMuted = crearEstiloDato(workbook, HorizontalAlignment.LEFT, COLOR_TEXTO_MUTED)
        val dataStyleCenterMuted = crearEstiloDato(workbook, HorizontalAlignment.CENTER, COLOR_TEXTO_MUTED)
        val gradeStyleMuted = workbook.createCellStyle().apply {
            cloneStyleFrom(dataStyleCenterMuted)
            dataFormat = integerFormat
        }
        val averageStyleMuted = workbook.createCellStyle().apply {
            cloneStyleFrom(dataStyleCenterMuted)
            dataFormat = decimalFormat
        }

        // Estilos tipo "badge" para estados y pendientes
        val estadoActivoStyle = crearEstiloBadge(workbook, COLOR_ESTADO_ACTIVO_BG, COLOR_ESTADO_ACTIVO_TEXT, bold = true)
        val estadoBajaStyle = crearEstiloBadge(workbook, COLOR_ESTADO_BAJA_BG, COLOR_ESTADO_BAJA_TEXT, bold = true)
        val sinCalificarStyle = crearEstiloBadge(workbook, COLOR_SIN_CALIFICAR_BG, COLOR_SIN_CALIFICAR_TEXT, bold = false)
        val noEntregadoStyle = crearEstiloBadge(workbook, COLOR_NO_ENTREGADO_BG, COLOR_NO_ENTREGADO_TEXT, bold = false)

        val asignacionesOrdenadas = curso.asignaciones.sortedBy { it.id ?: 0L }
        val inscripcionesOrdenadas = curso.inscripciones.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.usuario.nombreCompleto ?: it.usuario.username }
        )

        // 1. Encabezados
        val headerRow: Row = sheet.createRow(0)
        val headers = mutableListOf("DNI", "Apellido y Nombre", "Usuario GitHub", "Email", "Estado")
        headers.addAll(asignacionesOrdenadas.map { it.titulo })
        headers.add("Promedio")

        headers.forEachIndexed { colIndex, headerTitle ->
            val cell: Cell = headerRow.createCell(colIndex)
            cell.setCellValue(headerTitle)
            cell.setCellStyle(headerStyle)
        }

        // 2. Filas de estudiantes
        var rowIndex = 1
        for (inscripcion in inscripcionesOrdenadas) {
            val row: Row = sheet.createRow(rowIndex++)
            val usuario = inscripcion.usuario
            val esActivo = inscripcion.activa
            var colIndex = 0

            val currentLeftStyle = if (esActivo) dataStyleLeft else dataStyleLeftMuted
            val currentCenterStyle = if (esActivo) dataStyleCenter else dataStyleCenterMuted
            val currentGradeStyle = if (esActivo) gradeStyle else gradeStyleMuted
            val currentAverageStyle = if (esActivo) averageStyle else averageStyleMuted

            // DNI
            val cellDni: Cell = row.createCell(colIndex++)
            cellDni.setCellValue(usuario.dni ?: "-")
            cellDni.setCellStyle(currentCenterStyle)

            // Apellido y Nombre
            val cellNombre: Cell = row.createCell(colIndex++)
            cellNombre.setCellValue(usuario.nombreCompleto ?: usuario.username)
            cellNombre.setCellStyle(currentLeftStyle)

            // Usuario GitHub
            val cellUser: Cell = row.createCell(colIndex++)
            cellUser.setCellValue(usuario.username)
            cellUser.setCellStyle(currentLeftStyle)

            // Email
            val cellEmail: Cell = row.createCell(colIndex++)
            cellEmail.setCellValue(usuario.email ?: "-")
            cellEmail.setCellStyle(currentLeftStyle)

            // Estado (verde pastel si Activo, rojo pastel si Dado de baja)
            val cellEstado: Cell = row.createCell(colIndex++)
            if (esActivo) {
                cellEstado.setCellValue("Activo")
                cellEstado.setCellStyle(estadoActivoStyle)
            } else {
                cellEstado.setCellValue("Dado de baja")
                cellEstado.setCellStyle(estadoBajaStyle)
            }

            // Asignaciones y Calificaciones
            val notasEstudiante = mutableListOf<Double>()
            for (asignacion in asignacionesOrdenadas) {
                val cellAsignacion: Cell = row.createCell(colIndex++)
                val grupo = asignacion.grupos.firstOrNull { it.tieneIntegrante(usuario.username) }

                when {
                    grupo?.calificacion != null -> {
                        val nota = grupo.calificacion!!.toDouble()
                        notasEstudiante.add(nota)
                        cellAsignacion.setCellValue(nota)
                        cellAsignacion.setCellStyle(currentGradeStyle)
                    }
                    grupo != null && grupo.entregada -> {
                        cellAsignacion.setCellValue("Sin calificar")
                        cellAsignacion.setCellStyle(sinCalificarStyle)
                    }
                    else -> {
                        cellAsignacion.setCellValue("No entregado")
                        cellAsignacion.setCellStyle(noEntregadoStyle)
                    }
                }
            }

            // Promedio
            val cellPromedio: Cell = row.createCell(colIndex)
            if (notasEstudiante.isNotEmpty()) {
                val promedio = notasEstudiante.average()
                cellPromedio.setCellValue(promedio)
                cellPromedio.setCellStyle(currentAverageStyle)
            } else {
                cellPromedio.setCellValue("-")
                cellPromedio.setCellStyle(currentCenterStyle)
            }
        }

        // 3. Ajuste de ancho de columnas
        val totalCols = headers.size
        for (col in 0 until totalCols) {
            sheet.autoSizeColumn(col)
            val currentWidth = sheet.getColumnWidth(col)
            val paddedWidth = (currentWidth + 1200).coerceAtMost(256 * 50)
            sheet.setColumnWidth(col, paddedWidth)
        }

        val outputStream = ByteArrayOutputStream()
        workbook.write(outputStream)
        workbook.close()

        val nombreArchivo = curso.generarNombreArchivoExportacion()
        return ArchivoExportadoDTO(
            nombreArchivo = nombreArchivo,
            contenido = outputStream.toByteArray()
        )
    }

    private fun crearEstiloEncabezado(workbook: Workbook): CellStyle {
        val style = workbook.createCellStyle()
        val font = workbook.createFont().apply {
            bold = true
            color = IndexedColors.BLACK.index
        }
        style.setFont(font)
        style.fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
        aplicarBordes(style)
        return style
    }

    private fun crearEstiloDato(
        workbook: Workbook,
        alignment: HorizontalAlignment,
        textRgb: Triple<Int, Int, Int>? = null
    ): CellStyle {
        val style = workbook.createCellStyle()
        if (textRgb != null) {
            val colorMap = DefaultIndexedColorMap()
            val textColor = XSSFColor(byteArrayOf(textRgb.first.toByte(), textRgb.second.toByte(), textRgb.third.toByte()), colorMap)
            val font = (workbook.createFont() as XSSFFont).apply {
                setColor(textColor)
            }
            style.setFont(font)
        }
        style.alignment = alignment
        style.verticalAlignment = VerticalAlignment.CENTER
        aplicarBordes(style)
        return style
    }

    private fun crearEstiloBadge(
        workbook: Workbook,
        bgRgb: Triple<Int, Int, Int>,
        textRgb: Triple<Int, Int, Int>,
        bold: Boolean = false
    ): CellStyle {
        val style = workbook.createCellStyle() as XSSFCellStyle
        val colorMap = DefaultIndexedColorMap()
        val bgColor = XSSFColor(byteArrayOf(bgRgb.first.toByte(), bgRgb.second.toByte(), bgRgb.third.toByte()), colorMap)
        val textColor = XSSFColor(byteArrayOf(textRgb.first.toByte(), textRgb.second.toByte(), textRgb.third.toByte()), colorMap)

        val font = (workbook.createFont() as XSSFFont).apply {
            this.bold = bold
            setColor(textColor)
        }
        style.setFont(font)
        style.setFillForegroundColor(bgColor)
        style.fillPattern = FillPatternType.SOLID_FOREGROUND
        style.alignment = HorizontalAlignment.CENTER
        style.verticalAlignment = VerticalAlignment.CENTER
        aplicarBordes(style)
        return style
    }

    private fun aplicarBordes(style: CellStyle) {
        style.borderBottom = BorderStyle.THIN
        style.borderTop = BorderStyle.THIN
        style.borderRight = BorderStyle.THIN
        style.borderLeft = BorderStyle.THIN
    }
}
