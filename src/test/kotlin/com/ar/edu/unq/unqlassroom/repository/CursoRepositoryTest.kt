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
class CursoRepositoryTest {

    @Autowired
    private lateinit var cursoRepository: CursoRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Autowired
    private lateinit var inscripcionRepository: InscripcionRepository

    @Test
    fun `findCursosParaDocente returns only courses owned by specified docente`() {
        val docente1 = usuarioRepository.save(Usuario(username = "profe_alberto", esDocente = true))
        val docente2 = usuarioRepository.save(Usuario(username = "profe_beatriz", esDocente = true))

        cursoRepository.save(Curso(materia = "Estructuras de Datos", anio = 2026, semestre = 2, comision = 1, owner = docente1))
        cursoRepository.save(Curso(materia = "Bases de Datos", anio = 2026, semestre = 2, comision = 2, owner = docente1))
        cursoRepository.save(Curso(materia = "Sistemas Operativos", anio = 2026, semestre = 2, comision = 1, owner = docente2))

        val cursosDocente1 = cursoRepository.findCursosParaDocente("profe_alberto")

        assertEquals(2, cursosDocente1.size)
        assertTrue(cursosDocente1.any { it.materia == "Estructuras de Datos" })
        assertTrue(cursosDocente1.any { it.materia == "Bases de Datos" })
        assertFalse(cursosDocente1.any { it.materia == "Sistemas Operativos" })
    }

    @Test
    fun `findCursosParaAlumno returns courses where student is enrolled and not the owner`() {
        val docente = usuarioRepository.save(Usuario(username = "profe_titular", esDocente = true))
        val alumno = usuarioRepository.save(Usuario(username = "alumno_lucas", esDocente = false))

        // Curso 1: alumno inscripto, owner docente -> DEBE retornar
        val curso1 = cursoRepository.save(Curso(materia = "Redes de Computadoras", anio = 2026, semestre = 2, comision = 1, owner = docente))
        val inscripcion1 = inscripcionRepository.save(Inscripcion(curso = curso1, usuario = alumno))
        curso1.inscripciones.add(inscripcion1)

        // Curso 2: alumno inscripto pero es el owner -> NO DEBE retornar en cursos para alumno
        val curso2 = cursoRepository.save(Curso(materia = "Laboratorio", anio = 2026, semestre = 2, comision = 1, owner = alumno))
        val inscripcion2 = inscripcionRepository.save(Inscripcion(curso = curso2, usuario = alumno))
        curso2.inscripciones.add(inscripcion2)

        // Curso 3: alumno NO inscripto -> NO DEBE retornar
        cursoRepository.save(Curso(materia = "Matemática", anio = 2026, semestre = 2, comision = 1, owner = docente))

        val cursosAlumno = cursoRepository.findCursosParaAlumno("alumno_lucas")

        assertEquals(1, cursosAlumno.size)
        assertEquals("Redes de Computadoras", cursosAlumno[0].materia)
    }
}
