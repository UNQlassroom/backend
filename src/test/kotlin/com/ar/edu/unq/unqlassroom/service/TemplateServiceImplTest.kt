package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.exception.UsuarioNoEsDocenteException
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoResponse
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.ar.edu.unq.unqlassroom.service.impl.TemplateServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.junit.jupiter.MockitoExtension

@ExtendWith(MockitoExtension::class)
class TemplateServiceImplTest {

    @Mock
    private lateinit var usuarioService: UsuarioService

    @Mock
    private lateinit var gitHubRepoService: GitHubRepoService

    @Mock
    private lateinit var repositorioRepository: RepositorioRepository

    @InjectMocks
    private lateinit var templateService: TemplateServiceImpl

    private fun anyRepositorio(): Repositorio {
        any(Repositorio::class.java)
        return Repositorio(nombre = "dummy", htmlUrl = "https://dummy")
    }

    @Test
    fun `crearTemplateRepository delegates to gitHubRepoService and saves to DB`() {
        val repoResponse = GitHubRepoResponse(
            name = "template-base-kotlin",
            fullName = "unqlassroom/template-base-kotlin",
            htmlUrl = "https://github.com/unqlassroom/template-base-kotlin",
            description = "Template base para ejercicios Kotlin",
            isTemplate = true
        )
        val savedRepo = Repositorio(
            id = 1L,
            nombre = "template-base-kotlin",
            htmlUrl = "https://github.com/unqlassroom/template-base-kotlin",
            esTemplate = true,
            descripcion = "Template base para ejercicios Kotlin"
        )

        `when`(usuarioService.obtenerDocente("profe_test")).thenReturn(Usuario(username = "profe_test", esDocente = true))
        `when`(gitHubRepoService.createTemplateRepository("template-base-kotlin", "Template base para ejercicios Kotlin")).thenReturn(repoResponse)
        `when`(repositorioRepository.save(anyRepositorio())).thenReturn(savedRepo)

        val result = templateService.crearTemplateRepository("template-base-kotlin", "Template base para ejercicios Kotlin", "profe_test")

        assertEquals("template-base-kotlin", result.nombre)
        assertEquals("https://github.com/unqlassroom/template-base-kotlin", result.htmlUrl)
        assertTrue(result.esTemplate)
        assertEquals("Template base para ejercicios Kotlin", result.descripcion)
        verify(usuarioService).obtenerDocente("profe_test")
        verify(gitHubRepoService).createTemplateRepository("template-base-kotlin", "Template base para ejercicios Kotlin")

        val captor = ArgumentCaptor.forClass(Repositorio::class.java)
        verify(repositorioRepository).save(captor.capture())
        val repoGuardado = captor.value
        assertEquals("template-base-kotlin", repoGuardado.nombre)
        assertEquals("https://github.com/unqlassroom/template-base-kotlin", repoGuardado.htmlUrl)
        assertTrue(repoGuardado.esTemplate)
        assertEquals("Template base para ejercicios Kotlin", repoGuardado.descripcion)
    }

    @Test
    fun `crearTemplateRepository throws exception if user is not docente`() {
        `when`(usuarioService.obtenerDocente("alumno_test")).thenThrow(UsuarioNoEsDocenteException("alumno_test"))

        assertThrows<UsuarioNoEsDocenteException> {
            templateService.crearTemplateRepository("tmpl", null, "alumno_test")
        }
    }

    @Test
    fun `listarTemplates returns templates from repository`() {
        val repo1 = Repositorio(
            id = 1L,
            nombre = "template-1",
            htmlUrl = "https://github.com/unqlassroom/template-1",
            esTemplate = true,
            descripcion = "Template 1"
        )
        `when`(usuarioService.obtenerDocente("profe_test")).thenReturn(Usuario(username = "profe_test", esDocente = true))
        `when`(repositorioRepository.findByEsTemplateTrueAndEliminadoFalseOrderByNombreAsc()).thenReturn(listOf(repo1))

        val result = templateService.listarTemplates("profe_test")

        assertEquals(1, result.size)
        assertEquals("template-1", result[0].nombre)
        assertTrue(result[0].esTemplate)
        verify(usuarioService).obtenerDocente("profe_test")
        verify(repositorioRepository).findByEsTemplateTrueAndEliminadoFalseOrderByNombreAsc()
    }

    @Test
    fun `listarTemplates throws exception if user is not docente`() {
        `when`(usuarioService.obtenerDocente("alumno_test")).thenThrow(UsuarioNoEsDocenteException("alumno_test"))

        assertThrows<UsuarioNoEsDocenteException> {
            templateService.listarTemplates("alumno_test")
        }
    }
}
