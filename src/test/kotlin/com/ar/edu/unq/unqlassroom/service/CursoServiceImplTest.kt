package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.controller.dtos.AgregarAlumnosRequestDTO
import com.ar.edu.unq.unqlassroom.controller.dtos.CursoRequestDTO
import com.ar.edu.unq.unqlassroom.errors.CursoNotFoundException
import com.ar.edu.unq.unqlassroom.errors.ForbiddenException
import com.ar.edu.unq.unqlassroom.errors.UsuarioNotFoundException
import com.ar.edu.unq.unqlassroom.github.GitHubOrgMembershipResponse
import com.ar.edu.unq.unqlassroom.github.GitHubOrgService
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Inscripcion
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.CursoRepository
import com.ar.edu.unq.unqlassroom.repository.InscripcionRepository
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import com.ar.edu.unq.unqlassroom.service.impl.CursoServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class CursoServiceImplTest {

    @Mock
    private lateinit var cursoRepository: CursoRepository

    @Mock
    private lateinit var usuarioService: UsuarioService

    @Mock
    private lateinit var inscripcionRepository: InscripcionRepository

    @Mock
    private lateinit var gitHubOrgService: GitHubOrgService

    @InjectMocks
    private lateinit var cursoService: CursoServiceImpl

    private fun anyCurso(): Curso {
        Mockito.any(Curso::class.java)
        return Curso(materia = "", anio = 0, semestre = 1, comision = 1)
    }

    @Test
    fun `crearCurso saves curso with owner and description without creating github repo`() {
        val requestDTO = CursoRequestDTO(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
        )

        val docente = Usuario(id = 10L, username = "profe_test", esDocente = true)
        `when`(usuarioService.obtenerDocente("profe_test")).thenReturn(docente)

        `when`(cursoRepository.save(anyCurso())).thenAnswer { invocation ->
            val curso = invocation.getArgument<Curso>(0)
            Curso(
                id = 1L,
                materia = curso.materia,
                anio = curso.anio,
                semestre = curso.semestre,
                comision = curso.comision,
                descripcion = curso.descripcion,
                owner = curso.owner,
            )
        }

        val result = cursoService.crearCurso(requestDTO, "profe_test")

        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals("Estructuras de Datos", result.materia)
        assertEquals(2026, result.anio)
        assertEquals(1, result.semestre)
        assertEquals(1, result.comision)
        assertEquals("Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1", result.descripcion)
        assertEquals("profe_test", result.ownerUsername)
    }

    @Test
    fun `generarDescripcion produces the expected generic description`() {
        val curso = Curso(
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1
        )
        assertEquals("Curso de Estructuras de Datos - Año 2026 - Semestre 1 - Comisión 1", curso.generarDescripcion())
    }

    @Test
    fun `agregarAlumnos invites students to github org and creates inscripcion with org membership state`() {
        val owner = Usuario(id = 10L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        `when`(usuarioService.obtenerOCrearAlumno(Mockito.anyString())).thenAnswer { invocation ->
            val u = invocation.getArgument<String>(0)
            Usuario(id = 1L, username = u, esDocente = false)
        }
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(Mockito.anyLong(), Mockito.anyString())).thenReturn(null)
        `when`(inscripcionRepository.save(Mockito.any(Inscripcion::class.java))).thenAnswer { it.getArgument(0) }

        `when`(gitHubOrgService.invitarMiembro("alumno1")).thenReturn(
            GitHubOrgMembershipResponse(state = "pending", role = "member")
        )
        `when`(gitHubOrgService.invitarMiembro("alumno2")).thenReturn(
            GitHubOrgMembershipResponse(state = "active", role = "member")
        )

        val request = AgregarAlumnosRequestDTO(
            usernames = listOf("alumno1", "alumno2", "alumno1 ")
        )

        val response = cursoService.agregarAlumnos(1L, request, "profe_test")

        assertEquals(1L, response.cursoId)
        assertNull(response.repoName)
        assertEquals(2, response.alumnos.size)
        assertEquals("alumno1", response.alumnos[0].username)
        assertEquals("pending", response.alumnos[0].state)
        assertEquals("member", response.alumnos[0].role)

        assertEquals("alumno2", response.alumnos[1].username)
        assertEquals("active", response.alumnos[1].state)
        assertEquals("member", response.alumnos[1].role)

        verify(gitHubOrgService).invitarMiembro("alumno1")
        verify(gitHubOrgService).invitarMiembro("alumno2")
        verify(inscripcionRepository, Mockito.times(2)).save(Mockito.any(Inscripcion::class.java))
    }

    @Test
    fun `agregarAlumnos throws CursoNotFoundException when curso does not exist`() {
        `when`(cursoRepository.findById(99L)).thenReturn(Optional.empty())

        val exception = assertThrows<CursoNotFoundException> {
            cursoService.agregarAlumnos(99L, AgregarAlumnosRequestDTO(listOf("alumno1")), "profe_test")
        }

        assertEquals("Curso no encontrado", exception.message)
    }

    @Test
    fun `agregarAlumnos throws ForbiddenException when solicitante is not the owner`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val exception = assertThrows<ForbiddenException> {
            cursoService.agregarAlumnos(1L, AgregarAlumnosRequestDTO(listOf("alumno1")), "otro_profe")
        }

        assertEquals("Solo el docente a cargo del curso puede agregar alumnos", exception.message)
    }

    @Test
    fun `sincronizarAlumnos updates pending students to active when accepted in github org`() {
        val owner = Usuario(id = 10L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val usuario1 = Usuario(id = 1L, username = "alumno1", esDocente = false)
        val usuario2 = Usuario(id = 2L, username = "alumno2", esDocente = false)
        val inscripcion1 = Inscripcion(usuario = usuario1, curso = curso, githubState = "pending")
        val inscripcion2 = Inscripcion(usuario = usuario2, curso = curso, githubState = "pending")

        `when`(inscripcionRepository.findByCursoId(1L)).thenReturn(listOf(inscripcion1, inscripcion2))

        // Alumno 1 aceptó la invitación, Alumno 2 sigue pendiente
        `when`(gitHubOrgService.obtenerMembresia("alumno1")).thenReturn(
            GitHubOrgMembershipResponse(state = "active", role = "member")
        )
        `when`(gitHubOrgService.obtenerMembresia("alumno2")).thenReturn(
            GitHubOrgMembershipResponse(state = "pending", role = "member")
        )

        val response = cursoService.sincronizarAlumnos(1L, "profe_test")

        assertEquals(1L, response.cursoId)
        assertEquals(2, response.alumnos.size)

        val a1 = response.alumnos.first { it.username == "alumno1" }
        assertEquals("active", a1.state)
        assertEquals("active", inscripcion1.githubState)

        val a2 = response.alumnos.first { it.username == "alumno2" }
        assertEquals("pending", a2.state)
        assertEquals("pending", inscripcion2.githubState)

        verify(inscripcionRepository).save(inscripcion1)
        verify(inscripcionRepository, Mockito.never()).save(inscripcion2)
    }

    @Test
    fun `sincronizarAlumnos throws ForbiddenException when solicitante is not owner`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val ex = assertThrows<ForbiddenException> {
            cursoService.sincronizarAlumnos(1L, "otro_docente")
        }
        assertEquals("Solo el docente a cargo del curso puede sincronizar alumnos", ex.message)
    }

    @Test
    fun `obtenerAlumnos returns enrolled members from DB without calling github`() {
        val owner = Usuario(id = 10L, username = "profe_test", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val usuario1 = Usuario(id = 1L, username = "alumno1", esDocente = false)
        val usuario2 = Usuario(id = 2L, username = "alumno2", esDocente = false)
        val inscripcion1 = Inscripcion(usuario = usuario1, curso = curso, githubState = "pending")
        val inscripcion2 = Inscripcion(usuario = usuario2, curso = curso, githubState = "active")

        `when`(inscripcionRepository.findByCursoId(1L)).thenReturn(listOf(inscripcion1, inscripcion2))

        val response = cursoService.obtenerAlumnos(1L, "profe_test")

        assertEquals(1L, response.cursoId)
        assertNull(response.repoName)
        assertEquals(2, response.alumnos.size)
        assertEquals("alumno1", response.alumnos[0].username)
        assertEquals("pending", response.alumnos[0].state)
        assertEquals("alumno2", response.alumnos[1].username)
        assertEquals("active", response.alumnos[1].state)

        verify(gitHubOrgService, Mockito.never()).obtenerMembresia(Mockito.anyString(), Mockito.nullable(String::class.java))
    }

    @Test
    fun `crearCurso propagates ForbiddenException when owner is not docente`() {
        val requestDTO = CursoRequestDTO(
            materia = "Redes",
            anio = 2026,
            semestre = 1,
            comision = 1,
        )
        `when`(usuarioService.obtenerDocente("alumno_infiltrado"))
            .thenThrow(ForbiddenException("El usuario alumno_infiltrado no tiene permisos de docente"))

        val ex = assertThrows<ForbiddenException> {
            cursoService.crearCurso(requestDTO, "alumno_infiltrado")
        }
        assertEquals("El usuario alumno_infiltrado no tiene permisos de docente", ex.message)
    }

    @Test
    fun `crearCurso propagates UsuarioNotFoundException when owner does not exist`() {
        val requestDTO = CursoRequestDTO(
            materia = "Redes",
            anio = 2026,
            semestre = 1,
            comision = 1,
        )
        `when`(usuarioService.obtenerDocente("fantasma"))
            .thenThrow(UsuarioNotFoundException("Usuario no encontrado: fantasma"))

        val ex = assertThrows<UsuarioNotFoundException> {
            cursoService.crearCurso(requestDTO, "fantasma")
        }
        assertEquals("Usuario no encontrado: fantasma", ex.message)
    }

    @Test
    fun `Inscripcion defaults githubRole to push and githubState to pending`() {
        val curso = Curso(materia = "BD", anio = 2026, semestre = 1, comision = 1)
        val usuario = Usuario(username = "alumno1")
        val inscripcion = Inscripcion(curso = curso, usuario = usuario)

        assertEquals("push", inscripcion.githubRole)
        assertEquals("pending", inscripcion.githubState)
    }

    @Test
    fun `obtenerAlumnos returns members when solicitante is enrolled alumno`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        val alumno = Usuario(id = 2L, username = "alumno_inscripto", esDocente = false)
        val inscripcion = Inscripcion(curso = curso, usuario = alumno)
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(1L, "alumno_inscripto")).thenReturn(inscripcion)
        `when`(inscripcionRepository.findByCursoId(1L)).thenReturn(emptyList())

        val response = cursoService.obtenerAlumnos(1L, "alumno_inscripto")
        assertNotNull(response)
        assertEquals(1L, response.cursoId)
    }

    @Test
    fun `obtenerAlumnos throws ForbiddenException when solicitante is neither owner nor enrolled alumno`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(1L, "infiltrado")).thenReturn(null)

        val ex = assertThrows<ForbiddenException> {
            cursoService.obtenerAlumnos(1L, "infiltrado")
        }
        assertEquals("No tiene permisos para ver los alumnos de este curso", ex.message)
    }

    @Test
    fun `obtenerCursos for docente calls findCursosParaDocente and returns DTOs`() {
        val curso = Curso(id = 1L, materia = "Estructuras", anio = 2026, semestre = 1, comision = 1)
        `when`(cursoRepository.findCursosParaDocente("profe_test")).thenReturn(listOf(curso))

        val result = cursoService.obtenerCursos("profe_test", true)

        assertEquals(1, result.size)
        assertEquals("Estructuras", result[0].materia)
        verify(cursoRepository).findCursosParaDocente("profe_test")
        verify(cursoRepository, Mockito.never()).findCursosParaAlumno(Mockito.anyString())
    }

    @Test
    fun `obtenerCursos for alumno calls findCursosParaAlumno and returns DTOs`() {
        val curso = Curso(id = 2L, materia = "Redes", anio = 2026, semestre = 1, comision = 2)
        `when`(cursoRepository.findCursosParaAlumno("alumno_test")).thenReturn(listOf(curso))

        val result = cursoService.obtenerCursos("alumno_test", false)

        assertEquals(1, result.size)
        assertEquals("Redes", result[0].materia)
        verify(cursoRepository).findCursosParaAlumno("alumno_test")
        verify(cursoRepository, Mockito.never()).findCursosParaDocente(Mockito.anyString())
    }

    @Test
    fun `obtenerCurso returns curso when solicitante is owner`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            descripcion = "desc",
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))

        val result = cursoService.obtenerCurso(1L, "profe_owner")

        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals("Estructuras de Datos", result.materia)
        assertEquals("profe_owner", result.ownerUsername)
    }

    @Test
    fun `obtenerCurso returns curso when solicitante is enrolled alumno`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            descripcion = "desc",
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        val alumno = Usuario(id = 2L, username = "alumno_inscripto", esDocente = false)
        val inscripcion = Inscripcion(curso = curso, usuario = alumno)
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(1L, "alumno_inscripto")).thenReturn(inscripcion)

        val result = cursoService.obtenerCurso(1L, "alumno_inscripto")

        assertNotNull(result)
        assertEquals(1L, result.id)
        assertEquals("Estructuras de Datos", result.materia)
    }

    @Test
    fun `obtenerCurso throws CursoNotFoundException when curso does not exist`() {
        `when`(cursoRepository.findById(999L)).thenReturn(Optional.empty())

        val ex = assertThrows<CursoNotFoundException> {
            cursoService.obtenerCurso(999L, "cualquiera")
        }
        assertEquals("Curso no encontrado", ex.message)
    }

    @Test
    fun `obtenerCurso throws ForbiddenException when solicitante is neither owner nor enrolled alumno`() {
        val owner = Usuario(id = 10L, username = "profe_owner", esDocente = true)
        val curso = Curso(
            id = 1L,
            materia = "Estructuras de Datos",
            anio = 2026,
            semestre = 1,
            comision = 1,
            owner = owner
        )
        `when`(cursoRepository.findById(1L)).thenReturn(Optional.of(curso))
        `when`(inscripcionRepository.findByCursoIdAndUsuarioUsername(1L, "infiltrado")).thenReturn(null)

        val ex = assertThrows<ForbiddenException> {
            cursoService.obtenerCurso(1L, "infiltrado")
        }
        assertEquals("No tiene permisos para acceder a este curso", ex.message)
    }
}
