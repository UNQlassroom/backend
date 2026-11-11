package com.ar.edu.unq.unqlassroom.service

import com.ar.edu.unq.unqlassroom.exception.GitHubNotFoundException
import com.ar.edu.unq.unqlassroom.exception.RepositorioExternoNoEncontradoException
import com.ar.edu.unq.unqlassroom.exception.RepositorioExternoPrivadoException
import com.ar.edu.unq.unqlassroom.exception.UsuarioNoEsDocenteException
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubPublicRepoDetails
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoResponse
import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.model.Usuario
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.ar.edu.unq.unqlassroom.service.impl.TemplateServiceImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    private lateinit var asyncService: AsyncService

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
            templateAprobado = false,
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
        assertFalse(result.templateAprobado)
        assertEquals("Template base para ejercicios Kotlin", result.descripcion)
        verify(usuarioService).obtenerDocente("profe_test")
        verify(gitHubRepoService).createTemplateRepository("template-base-kotlin", "Template base para ejercicios Kotlin")

        val captor = ArgumentCaptor.forClass(Repositorio::class.java)
        verify(repositorioRepository).save(captor.capture())
        val repoGuardado = captor.value
        assertEquals("template-base-kotlin", repoGuardado.nombre)
        assertEquals("https://github.com/unqlassroom/template-base-kotlin", repoGuardado.htmlUrl)
        assertTrue(repoGuardado.esTemplate)
        assertFalse(repoGuardado.templateAprobado)
        assertEquals(55L, repoGuardado.githubRepoId)
    }

    @Test
    fun `crearTemplateRepository with repoAImportar validates public repo, creates org repo, saves unapproved template to DB and triggers asyncService`() {
        val sourceUrl = "https://github.com/profe/tp1-publico"
        val publicRepoDetails = GitHubPublicRepoDetails(
            defaultBranch = "main",
            private = false,
        )
        val createdRepoResponse = GitHubRepoResponse(
            id = 999L,
            name = "template-importado",
            fullName = "unqlassroom/template-importado",
            htmlUrl = "https://github.com/unqlassroom/template-importado",
            description = "Importado de repo personal",
            isTemplate = false,
        )
        val savedRepo = Repositorio(
            id = 10L,
            nombre = "template-importado",
            htmlUrl = "https://github.com/unqlassroom/template-importado",
            esTemplate = true,
            descripcion = "Importado de repo personal",
            githubRepoId = 999L,
            templateAprobado = false,
        )

        val docente = Usuario(username = "profe_test", esDocente = true)

        `when`(usuarioService.obtenerDocente("profe_test")).thenReturn(docente)
        `when`(gitHubRepoService.parsearOwnerYRepo(sourceUrl)).thenReturn(Pair("profe", "tp1-publico"))
        `when`(gitHubRepoService.getPublicRepoDetails("profe", "tp1-publico")).thenReturn(publicRepoDetails)
        `when`(gitHubRepoService.createOrgRepository(
            name = "template-importado",
            description = "Importado de repo personal",
            private = true,
            autoInit = true,
            isTemplate = false,
        )).thenReturn(createdRepoResponse)
        `when`(repositorioRepository.save(anyRepositorio())).thenReturn(savedRepo)

        val result = templateService.crearTemplateRepository(
            name = "template-importado",
            description = "Importado de repo personal",
            solicitanteUsername = "profe_test",
            repoAImportar = sourceUrl,
        )

        assertEquals("template-importado", result.nombre)
        assertEquals(999L, result.githubRepoId)
        assertFalse(result.templateAprobado)
        assertTrue(result.esTemplate)

        verify(gitHubRepoService).parsearOwnerYRepo(sourceUrl)
        verify(gitHubRepoService).getPublicRepoDetails("profe", "tp1-publico")
        verify(gitHubRepoService).createOrgRepository(
            name = "template-importado",
            description = "Importado de repo personal",
            private = true,
            autoInit = true,
            isTemplate = false,
        )
        verify(repositorioRepository).save(anyRepositorio())
        verify(asyncService).importarYEstandarizarTemplateAsincronicamente(
            targetRepoName = "template-importado",
            sourceRepoUrl = sourceUrl,
        )
    }

    @Test
    fun `crearTemplateRepository throws RepositorioExternoPrivadoException when external repo is private`() {
        val sourceUrl = "https://github.com/profe/tp1-privado"
        val privateRepoDetails = GitHubPublicRepoDetails(
            defaultBranch = "main",
            private = true,
        )

        `when`(usuarioService.obtenerDocente("profe_test")).thenReturn(Usuario(username = "profe_test", esDocente = true))
        `when`(gitHubRepoService.parsearOwnerYRepo(sourceUrl)).thenReturn(Pair("profe", "tp1-privado"))
        `when`(gitHubRepoService.getPublicRepoDetails("profe", "tp1-privado")).thenReturn(privateRepoDetails)

        assertThrows<RepositorioExternoPrivadoException> {
            templateService.crearTemplateRepository(
                name = "template-error",
                description = "Desc",
                solicitanteUsername = "profe_test",
                repoAImportar = sourceUrl,
            )
        }

        verify(gitHubRepoService).parsearOwnerYRepo(sourceUrl)
        verify(gitHubRepoService).getPublicRepoDetails("profe", "tp1-privado")
        verifyNoMoreInteractions(gitHubRepoService)
        verifyNoInteractions(repositorioRepository)
        verifyNoInteractions(asyncService)
    }

    @Test
    fun `crearTemplateRepository throws RepositorioExternoNoEncontradoException when external repo is not found`() {
        val sourceUrl = "https://github.com/profe/inexistente"

        `when`(usuarioService.obtenerDocente("profe_test")).thenReturn(Usuario(username = "profe_test", esDocente = true))
        `when`(gitHubRepoService.parsearOwnerYRepo(sourceUrl)).thenReturn(Pair("profe", "inexistente"))
        `when`(gitHubRepoService.getPublicRepoDetails("profe", "inexistente")).thenThrow(GitHubNotFoundException("Not found"))

        assertThrows<RepositorioExternoNoEncontradoException> {
            templateService.crearTemplateRepository(
                name = "template-error",
                description = "Desc",
                solicitanteUsername = "profe_test",
                repoAImportar = sourceUrl,
            )
        }

        verify(gitHubRepoService).parsearOwnerYRepo(sourceUrl)
        verify(gitHubRepoService).getPublicRepoDetails("profe", "inexistente")
        verifyNoMoreInteractions(gitHubRepoService)
        verifyNoInteractions(repositorioRepository)
        verifyNoInteractions(asyncService)
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
