package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.Asignacion
import com.ar.edu.unq.unqlassroom.model.Curso
import com.ar.edu.unq.unqlassroom.model.TipoAsignacion
import com.ar.edu.unq.unqlassroom.model.Usuario
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@SpringBootTest
@Transactional
class AsignacionRepositoryTest {

    @Autowired
    private lateinit var asignacionRepository: AsignacionRepository

    @Autowired
    private lateinit var cursoRepository: CursoRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Test
    fun `findByCursoId returns all assignments for the given course`() {
        val docente = usuarioRepository.save(Usuario(username = "docente_owner", esDocente = true))

        val curso1 = cursoRepository.save(Curso(materia = "Curso Alpha", anio = 2026, semestre = 2, comision = 1, owner = docente))
        val curso2 = cursoRepository.save(Curso(materia = "Curso Beta", anio = 2026, semestre = 2, comision = 1, owner = docente))

        val tp1 = Asignacion(titulo = "TP 1", tipo = TipoAsignacion.INDIVIDUAL, templateRepoName = "tmpl1", curso = curso1)
        val tp2 = Asignacion(titulo = "TP 2", tipo = TipoAsignacion.GRUPAL, templateRepoName = "tmpl2", curso = curso1)
        val tp3 = Asignacion(titulo = "TP 3", tipo = TipoAsignacion.INDIVIDUAL, templateRepoName = "tmpl3", curso = curso2)
        asignacionRepository.save(tp1)
        asignacionRepository.save(tp2)
        asignacionRepository.save(tp3)

        val asignacionesCurso1 = asignacionRepository.findByCursoId(curso1.id!!)

        assertEquals(2, asignacionesCurso1.size)
        assertTrue(asignacionesCurso1.any { it.titulo == "TP 1" })
        assertTrue(asignacionesCurso1.any { it.titulo == "TP 2" })
    }

    @Test
    fun `findByIdAndCursoId returns assignment only when both assignment id and course id match`() {
        val docente = usuarioRepository.save(Usuario(username = "docente_owner", esDocente = true))

        val curso1 = cursoRepository.save(Curso(materia = "Curso 1", anio = 2026, semestre = 2, comision = 1, owner = docente))
        val curso2 = cursoRepository.save(Curso(materia = "Curso 2", anio = 2026, semestre = 2, comision = 1, owner = docente))

        val tp = asignacionRepository.save(
            Asignacion(
                titulo = "TP Final",
                tipo = TipoAsignacion.GRUPAL,
                templateRepoName = "tmpl-final",
                fechaLimite = LocalDateTime.now().plusDays(7),
                curso = curso1
            )
        )

        val found = asignacionRepository.findByIdAndCursoId(tp.id!!, curso1.id!!)
        assertNotNull(found)
        assertEquals("TP Final", found?.titulo)

        // Si se busca con otro cursoId no debe retornar nada
        val notFound = asignacionRepository.findByIdAndCursoId(tp.id!!, curso2.id!!)
        assertNull(notFound)
    }
}
