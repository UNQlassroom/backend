package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.Inscripcion
import com.ar.edu.unq.unqlassroom.model.Usuario
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class InscripcionRepositoryTest {

    @Autowired
    private lateinit var inscripcionRepository: InscripcionRepository

    @Autowired
    private lateinit var cursoRepository: CursoRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Test
    fun `findByCursoId returns all inscriptions for a given course`() {
        val docente = usuarioRepository.save(Usuario(username = "docente_test", esDocente = true))
        val alumno1 = usuarioRepository.save(Usuario(username = "alumno_uno", esDocente = false))
        val alumno2 = usuarioRepository.save(Usuario(username = "alumno_dos", esDocente = false))
        val alumno3 = usuarioRepository.save(Usuario(username = "alumno_tres", esDocente = false))

        val curso1 = cursoRepository.save(Curso(materia = "Materia 1", anio = 2026, semestre = 2, comision = 1, owner = docente))
        val curso2 = cursoRepository.save(Curso(materia = "Materia 2", anio = 2026, semestre = 2, comision = 1, owner = docente))

        inscripcionRepository.save(Inscripcion(curso = curso1, usuario = alumno1))
        inscripcionRepository.save(Inscripcion(curso = curso1, usuario = alumno2))
        inscripcionRepository.save(Inscripcion(curso = curso2, usuario = alumno3))

        val inscripcionesCurso1 = inscripcionRepository.findByCursoId(curso1.id!!)

        assertEquals(2, inscripcionesCurso1.size)
        assertTrue(inscripcionesCurso1.any { it.usuario.username == "alumno_uno" })
        assertTrue(inscripcionesCurso1.any { it.usuario.username == "alumno_dos" })
    }

    @Test
    fun `findByCursoIdAndUsuarioUsername returns inscription when match exists and null otherwise`() {
        val docente = usuarioRepository.save(Usuario(username = "docente_test", esDocente = true))
        val alumno = usuarioRepository.save(Usuario(username = "alumno_inscripto", esDocente = false))

        val curso = cursoRepository.save(Curso(materia = "Materia A", anio = 2026, semestre = 2, comision = 1, owner = docente))

        inscripcionRepository.save(Inscripcion(curso = curso, usuario = alumno, githubRole = "write", githubState = "active"))

        val found = inscripcionRepository.findByCursoIdAndUsuarioUsername(curso.id!!, "alumno_inscripto")
        assertNotNull(found)
        assertEquals("alumno_inscripto", found?.usuario?.username)
        assertEquals("write", found?.githubRole)

        val notFound = inscripcionRepository.findByCursoIdAndUsuarioUsername(curso.id!!, "alumno_inexistente")
        assertNull(notFound)
    }
}
