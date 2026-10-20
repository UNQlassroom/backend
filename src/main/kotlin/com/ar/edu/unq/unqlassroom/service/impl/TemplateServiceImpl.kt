package com.ar.edu.unq.unqlassroom.service.impl

import com.ar.edu.unq.unqlassroom.integration.github.service.GitHubRepoService
import com.ar.edu.unq.unqlassroom.model.Repositorio
import com.ar.edu.unq.unqlassroom.repository.RepositorioRepository
import com.ar.edu.unq.unqlassroom.service.TemplateService
import com.ar.edu.unq.unqlassroom.service.UsuarioService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class TemplateServiceImpl(
    private val usuarioService: UsuarioService,
    private val gitHubRepoService: GitHubRepoService,
    private val repositorioRepository: RepositorioRepository,
) : TemplateService {

    override fun crearTemplateRepository(
        name: String,
        description: String?,
        solicitanteUsername: String
    ): Repositorio {
        usuarioService.obtenerDocente(solicitanteUsername)

        val repoResponse = gitHubRepoService.createTemplateRepository(
            name = name,
            description = description,
        )

        val repositorio = Repositorio(
            nombre = repoResponse.name,
            htmlUrl = repoResponse.htmlUrl,
            esTemplate = true,
            descripcion = repoResponse.description,
        )

        return repositorioRepository.save(repositorio)
    }

    @Transactional(readOnly = true)
    override fun listarTemplates(solicitanteUsername: String): List<Repositorio> {
        usuarioService.obtenerDocente(solicitanteUsername)

        return repositorioRepository.findByEsTemplateTrueAndEliminadoFalseOrderByNombreAsc()
    }
}
