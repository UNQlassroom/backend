package com.ar.edu.unq.unqlassroom.model

import com.ar.edu.unq.unqlassroom.exception.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class AsignacionTest {

    private fun cursoDummy(): Curso =
        Curso(materia = "Base de Datos", anio = 2026, semestre = 1, comision = 1)

    @Test
    fun `nueva asignacion inicia en estado CREANDO y puede transicionar a LISTA o ERROR`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = cursoDummy()
        )

        assertEquals(EstadoAsignacion.CREANDO, asignacion.estado)

        asignacion.marcarComoLista()
        assertEquals(EstadoAsignacion.LISTA, asignacion.estado)

        asignacion.marcarComoError()
        assertEquals(EstadoAsignacion.ERROR, asignacion.estado)
    }

    @Test
    fun `validarEstadoLista throws AsignacionNoListaException when estado is CREANDO or ERROR and succeeds when LISTA`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = cursoDummy()
        )

        val exCreando = assertThrows<AsignacionNoListaException> {
            asignacion.validarEstadoLista()
        }
        assertEquals("ASIGNACION_NO_LISTA", exCreando.errorCode)

        asignacion.marcarComoError()
        val exError = assertThrows<AsignacionNoListaException> {
            asignacion.validarEstadoLista()
        }
        assertEquals("ASIGNACION_NO_LISTA", exError.errorCode)

        asignacion.marcarComoLista()
        assertDoesNotThrow { asignacion.validarEstadoLista() }
    }

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
        val ex = assertThrows<AsignacionVencidaException> {
            asignacionVencida.validarVencimiento()
        }
        assertEquals("No se puede entregar la asignación porque la fecha límite ha vencido", ex.message)
    }

    @Test
    fun `validarEstructuraGrupos INDIVIDUAL throws AsignacionIndividualConGruposException if grupos is not empty`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            curso = cursoDummy()
        )
        asignacion.grupos.add(GrupoAsignacion(nombre = "Grupo 1"))

        val ex = assertThrows<AsignacionIndividualConGruposException> {
            asignacion.validarEstructuraGrupos()
        }
        assertEquals("No se pueden especificar grupos para una asignación individual", ex.message)
    }

    @Test
    fun `validarEstructuraGrupos GRUPAL throws AsignacionGrupalSinGruposException or GrupoSinIntegrantesException`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl",
            curso = cursoDummy()
        )

        val ex1 = assertThrows<AsignacionGrupalSinGruposException> {
            asignacion.validarEstructuraGrupos()
        }
        assertEquals("Para una asignación grupal debe especificar al menos un grupo", ex1.message)

        asignacion.grupos.add(GrupoAsignacion(nombre = "Grupo Vacío", integrantes = mutableListOf()))
        val ex2 = assertThrows<GrupoSinIntegrantesException> {
            asignacion.validarEstructuraGrupos()
        }
        assertEquals("Todos los grupos deben tener al menos un integrante", ex2.message)
    }

    @Test
    fun `validarEstructuraGrupos GRUPAL throws NombreGrupoVacioException on blank group name`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl",
            curso = cursoDummy()
        )
        asignacion.grupos.add(GrupoAsignacion(nombre = "   ", integrantes = mutableListOf(Usuario(username = "u1"))))

        val ex = assertThrows<NombreGrupoVacioException> {
            asignacion.validarEstructuraGrupos()
        }
        assertEquals("El nombre del grupo no puede estar vacío", ex.message)
    }

    @Test
    fun `validarEstructuraGrupos GRUPAL throws NombreGrupoDuplicadoException or AlumnoEnMultiplesGruposException`() {
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

        val ex1 = assertThrows<NombreGrupoDuplicadoException> {
            asignacion.validarEstructuraGrupos()
        }
        assertEquals("No puede haber grupos con el mismo nombre en la misma asignación", ex1.message)

        asignacion.grupos.clear()
        asignacion.grupos.add(GrupoAsignacion(nombre = "Alfa", integrantes = mutableListOf(u1)))
        asignacion.grupos.add(GrupoAsignacion(nombre = "Beta", integrantes = mutableListOf(u1)))

        val ex2 = assertThrows<AlumnoEnMultiplesGruposException> {
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

        assertThrows<GrupoNoPerteneceAAsignacionException> {
            asignacion.buscarGrupo(99L)
        }
        assertThrows<UsuarioNoPerteneceAGrupoException> {
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
            estado = EstadoAsignacion.LISTA,
            curso = cursoDummy()
        )
        val u1 = Usuario(username = "alumno1")
        val u2 = Usuario(username = "alumno2")
        val g1 = GrupoAsignacion(id = 10L, nombre = "Alfa", integrantes = mutableListOf(u1))
        val g2 = GrupoAsignacion(id = 20L, nombre = "Beta", integrantes = mutableListOf(u2))
        asignacion.grupos.addAll(listOf(g1, g2))

        val paraOwner = asignacion.paraVisualizacionDe("profe", esOwner = true)
        assertEquals(2, paraOwner.grupos.size)
        assertEquals(EstadoAsignacion.LISTA, paraOwner.estado)

        val paraAlumno = asignacion.paraVisualizacionDe("alumno1", esOwner = false)
        assertEquals(1, paraAlumno.grupos.size)
        assertEquals("Alfa", paraAlumno.grupos[0].nombre)
        assertEquals(EstadoAsignacion.LISTA, paraAlumno.estado)
    }

    @Test
    fun `tieneEntregasOCalificaciones returns false when assignment has no submissions or grades`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl",
            estado = EstadoAsignacion.LISTA,
            curso = cursoDummy()
        )
        val g1 = GrupoAsignacion(id = 1L, nombre = "Grupo 1", entregada = false, cantidadEntregas = 0, calificacion = null)
        asignacion.grupos.add(g1)

        assertFalse(asignacion.tieneEntregasOCalificaciones())
        assertDoesNotThrow { asignacion.validarEliminacion() }
    }

    @Test
    fun `validarEliminacion throws AsignacionEnCreacionException when estado is CREANDO and allows ERROR or LISTA`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl",
            estado = EstadoAsignacion.CREANDO,
            curso = cursoDummy()
        )

        val ex = assertThrows<AsignacionEnCreacionException> {
            asignacion.validarEliminacion()
        }
        assertEquals("ASIGNACION_EN_CREACION", ex.errorCode)

        asignacion.marcarComoError()
        assertDoesNotThrow { asignacion.validarEliminacion() }

        asignacion.marcarComoLista()
        assertDoesNotThrow { asignacion.validarEliminacion() }
    }

    @Test
    fun `tieneEntregasOCalificaciones and validarEliminacion detect submissions or grades`() {
        val asignacion = Asignacion(
            titulo = "TP1",
            tipo = TipoAsignacion.GRUPAL,
            templateRepoName = "tmpl",
            estado = EstadoAsignacion.LISTA,
            curso = cursoDummy()
        )
        val g1 = GrupoAsignacion(id = 1L, nombre = "Grupo 1", entregada = false, cantidadEntregas = 0, calificacion = null)
        asignacion.grupos.add(g1)

        // Case 1: entregada = true
        g1.entregada = true
        assertTrue(asignacion.tieneEntregasOCalificaciones())
        assertThrows<AsignacionConEntregasOCalificacionesException> {
            asignacion.validarEliminacion()
        }

        // Case 2: cantidadEntregas > 0
        g1.entregada = false
        g1.cantidadEntregas = 2
        assertTrue(asignacion.tieneEntregasOCalificaciones())
        assertThrows<AsignacionConEntregasOCalificacionesException> {
            asignacion.validarEliminacion()
        }

        // Case 3: calificacion != null
        g1.cantidadEntregas = 0
        g1.calificacion = 8
        assertTrue(asignacion.tieneEntregasOCalificaciones())
        assertThrows<AsignacionConEntregasOCalificacionesException> {
            asignacion.validarEliminacion()
        }
    }

    @Test
    fun `actualizar modifies descripcion and fechaLimite correctly`() {
        val originalFecha = LocalDateTime.now().plusDays(2)
        val asignacion = Asignacion(
            titulo = "TP Original",
            descripcion = "Desc Original",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "repo-template",
            fechaLimite = originalFecha,
            estado = EstadoAsignacion.LISTA,
            curso = cursoDummy()
        )

        val nuevaFecha = LocalDateTime.now().plusDays(10)
        asignacion.actualizar("Nueva Descripcion", nuevaFecha)

        assertEquals("Nueva Descripcion", asignacion.descripcion)
        assertEquals(nuevaFecha, asignacion.fechaLimite)
        assertEquals("TP Original", asignacion.titulo)
        assertEquals(TipoAsignacion.INDIVIDUAL, asignacion.tipo)
        assertEquals("repo-template", asignacion.templateRepoName)
    }

    @Test
    fun `actualizar throws AsignacionNoListaException when estado is not LISTA`() {
        val asignacion = Asignacion(
            titulo = "TP Original",
            descripcion = "Desc Original",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "repo-template",
            estado = EstadoAsignacion.CREANDO,
            curso = cursoDummy()
        )

        assertThrows<AsignacionNoListaException> {
            asignacion.actualizar("Nueva Desc", null)
        }
    }

    @Test
    fun `actualizar with fechaLimite in the past throws FechaLimiteInvalidaException`() {
        val asignacion = Asignacion(
            titulo = "TP Original",
            descripcion = "Desc Original",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "repo-template",
            fechaLimite = LocalDateTime.now().plusDays(2),
            estado = EstadoAsignacion.LISTA,
            curso = cursoDummy()
        )

        val fechaPasada = LocalDateTime.now().minusDays(1)
        val ex = assertThrows<FechaLimiteInvalidaException> {
            asignacion.actualizar("Nueva Desc", fechaPasada)
        }
        assertEquals("La fecha límite no puede ser anterior a la fecha actual", ex.message)
    }

    @Test
    fun `actualizar with null fechaLimite allows clearing deadline`() {
        val asignacion = Asignacion(
            titulo = "TP Original",
            descripcion = "Desc Original",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "repo-template",
            fechaLimite = LocalDateTime.now().plusDays(2),
            estado = EstadoAsignacion.LISTA,
            curso = cursoDummy()
        )

        asignacion.actualizar("Nueva Desc", null)
        assertNull(asignacion.fechaLimite)
        assertEquals("Nueva Desc", asignacion.descripcion)
    }

    @Test
    fun `actualizar keeping the same past fechaLimite does not throw`() {
        val fechaPasada = LocalDateTime.now().minusDays(2)
        val asignacion = Asignacion(
            titulo = "TP Original",
            descripcion = "Desc Original",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "repo-template",
            fechaLimite = fechaPasada,
            estado = EstadoAsignacion.LISTA,
            curso = cursoDummy()
        )

        asignacion.actualizar("Corrigiendo solo la descripción", fechaPasada)
        assertEquals("Corrigiendo solo la descripción", asignacion.descripcion)
        assertEquals(fechaPasada, asignacion.fechaLimite)
    }
}
