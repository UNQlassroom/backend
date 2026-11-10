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

    @Mock
    private lateinit var templateStandardizationService: TemplateStandardizationService

    @InjectMocks
    private lateinit var templateService: TemplateServiceImpl

    private fun anyRepositorio(): Repositorio {
        any(Repositorio::class.java)
        return Repositorio(nombre = "dummy", htmlUrl = "https://dummy")
    }

    @Test
    fun `crearTemplateRepository delegates to gitHubRepoService and saves to DB when repoAImportar is null`() {
        val repoResponse = GitHubRepoResponse(
            id = 55L,
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
            descripcion = "Template base para ejercicios Kotlin",
            githubRepoId = 55L,
        )

        `when`(usuarioService.obtenerDocente("profe_test")).thenReturn(Usuario(username = "profe_test", esDocente = true))
        `when`(gitHubRepoService.createTemplateRepository("template-base-kotlin", "Template base para ejercicios Kotlin")).thenReturn(repoResponse)
        `when`(repositorioRepository.save(anyRepositorio())).thenReturn(savedRepo)

        val result = templateService.crearTemplateRepository(
            name = "template-base-kotlin",
            description = "Template base para ejercicios Kotlin",
            solicitanteUsername = "profe_test",
        )

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
        assertEquals(55L, repoGuardado.githubRepoId)
    }

    @Test
    fun `crearTemplateRepository with repoAImportar imports, sets template, standardizes and saves`() {
        val createdRepoResponse = GitHubRepoResponse(
            id = 999L,
            name = "template-importado",
            fullName = "unqlassroom/template-importado",
            htmlUrl = "https://github.com/unqlassroom/template-importado",
            description = "Importado de repo personal",
            isTemplate = false,
        )
        val standardizedRepo = Repositorio(
            id = 10L,
            nombre = "template-importado",
            htmlUrl = "https://github.com/unqlassroom/template-importado",
            esTemplate = true,
            descripcion = "Importado de repo personal",
            ultimoCommitSha = "sha999",
            templateAprobado = true,
        )

        val docente = Usuario(username = "profe_test", esDocente = true)

        `when`(usuarioService.obtenerDocente("profe_test")).thenReturn(docente)
        `when`(gitHubRepoService.createOrgRepository(
            name = "template-importado",
            description = "Importado de repo personal",
            private = true,
            autoInit = true,
            isTemplate = false,
        )).thenReturn(createdRepoResponse)
        `when`(gitHubRepoService.importRepository(
            targetRepoName = "template-importado",
            sourceRepoUrlOrSlug = "profe/tp1-publico",
            fallbackOwner = "profe_test",
        )).thenReturn(5)
        `when`(gitHubRepoService.setTemplateStatus("template-importado", true)).thenReturn(createdRepoResponse.copy(isTemplate = true))
        `when`(templateStandardizationService.asegurarTemplateEstandarizado("template-importado")).thenReturn(standardizedRepo)
        `when`(repositorioRepository.save(standardizedRepo)).thenReturn(standardizedRepo)

        val result = templateService.crearTemplateRepository(
            name = "template-importado",
            description = "Importado de repo personal",
            solicitanteUsername = "profe_test",
            repoAImportar = "profe/tp1-publico",
        )

        assertEquals("template-importado", result.nombre)
        assertEquals(999L, result.githubRepoId)
        assertTrue(result.templateAprobado)
        assertTrue(result.esTemplate)
        assertEquals("sha999", result.ultimoCommitSha)

        verify(gitHubRepoService).createOrgRepository(
            name = "template-importado",
            description = "Importado de repo personal",
            private = true,
            autoInit = true,
            isTemplate = false,
        )
        verify(gitHubRepoService).importRepository(
            targetRepoName = "template-importado",
            sourceRepoUrlOrSlug = "profe/tp1-publico",
            fallbackOwner = "profe_test",
        )
        verify(gitHubRepoService).setTemplateStatus("template-importado", true)
        verify(templateStandardizationService).asegurarTemplateEstandarizado("template-importado")
        verify(repositorioRepository).save(standardizedRepo)
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
