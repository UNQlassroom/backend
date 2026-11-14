package com.ar.edu.unq.unqlassroom.repository

import com.ar.edu.unq.unqlassroom.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class AdvertenciaGitRepositoryTest {

    @Autowired
    private lateinit var advertenciaGitRepository: AdvertenciaGitRepository

    @Autowired
    private lateinit var usuarioRepository: UsuarioRepository

    @Autowired
    private lateinit var cursoRepository: CursoRepository

    @Autowired
    private lateinit var asignacionRepository: AsignacionRepository

    @Autowired
    private lateinit var grupoAsignacionRepository: GrupoAsignacionRepository

    @Autowired
    private lateinit var repositorioRepository: RepositorioRepository

    @Test
    fun `persiste y consulta advertencias asociadas a repositorio, asignacion, grupo y alumno`() {
        val docente = usuarioRepository.save(Usuario(username = "docente_git", esDocente = true))
        val alumno = usuarioRepository.save(Usuario(username = "alumno_git", esDocente = false))

        val curso = cursoRepository.save(
            Curso(
                materia = "Ingeniería de Software",
                anio = 2026,
                semestre = 2,
                comision = 1,
                owner = docente
            )
        )

        val repo = repositorioRepository.save(
            Repositorio(
                nombre = "2026s2_c1_ing_tp1_alumno_git",
                htmlUrl = "https://github.com/UNQlassroom/2026s2_c1_ing_tp1_alumno_git"
            )
        )

        val asignacion = Asignacion(
            titulo = "TP1 Git",
            tipo = TipoAsignacion.INDIVIDUAL,
            templateRepoName = "tmpl-tp1",
            curso = curso
        )
        val grupo = GrupoAsignacion(
            asignacion = asignacion,
            repositorio = repo,
            integrantes = mutableListOf(alumno)
        )
        asignacion.grupos.add(grupo)
        val asignacionGuardada = asignacionRepository.save(asignacion)
        val grupoGuardado = asignacionGuardada.grupos.first()

        val advertenciaTamano = AdvertenciaGit(
            commitSha = "sha111",
            mensajeCommit = "feat: agregar muchos archivos de golpe",
            tipo = TipoAdvertenciaGit.COMMIT_NO_ATOMICO,
            mensajeExplicativo = "Detectamos un commit grande (+650 líneas / 18 archivos).",
            archivosModificados = 18,
            lineasAgregadas = 600,
            lineasEliminadas = 50,
            lineasModificadas = 650,
            autorUsername = alumno.username,
            repositorio = repo,
            grupoAsignacion = grupoGuardado,
            asignacion = asignacionGuardada,
            alumno = alumno,
        )

        val advertenciaMensaje = AdvertenciaGit(
            commitSha = "sha222",
            mensajeCommit = "fix",
            tipo = TipoAdvertenciaGit.MENSAJE_INADECUADO,
            mensajeExplicativo = "Se recomienda usar el formato Conventional Commits.",
            archivosModificados = 1,
            lineasAgregadas = 4,
            lineasEliminadas = 2,
            lineasModificadas = 6,
            autorUsername = alumno.username,
            repositorio = repo,
            grupoAsignacion = grupoGuardado,
            asignacion = asignacionGuardada,
            alumno = alumno,
        )

        advertenciaGitRepository.save(advertenciaTamano)
        advertenciaGitRepository.save(advertenciaMensaje)

        val porRepo = advertenciaGitRepository.findByRepositorioIdOrderByFechaCreacionDesc(repo.id!!)
        assertEquals(2, porRepo.size)

        val porGrupo = advertenciaGitRepository.findByGrupoAsignacionIdOrderByFechaCreacionDesc(grupoGuardado.id!!)
        assertEquals(2, porGrupo.size)

        val porAsignacion = advertenciaGitRepository.findByAsignacionIdOrderByFechaCreacionDesc(asignacionGuardada.id!!)
        assertEquals(2, porAsignacion.size)

        val porAlumno = advertenciaGitRepository.findByAlumnoIdOrderByFechaCreacionDesc(alumno.id!!)
        assertEquals(2, porAlumno.size)

        assertTrue(
            advertenciaGitRepository.existsByRepositorioIdAndCommitShaAndTipo(
                repo.id!!,
                "sha111",
                TipoAdvertenciaGit.COMMIT_NO_ATOMICO
            )
        )
        assertFalse(
            advertenciaGitRepository.existsByRepositorioIdAndCommitShaAndTipo(
                repo.id!!,
                "sha111",
                TipoAdvertenciaGit.MENSAJE_INADECUADO
            )
        )
    }
}

