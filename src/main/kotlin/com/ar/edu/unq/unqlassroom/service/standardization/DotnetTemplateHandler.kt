package com.ar.edu.unq.unqlassroom.service.standardization

import com.ar.edu.unq.unqlassroom.model.LenguajeProyecto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class DotnetTemplateHandler(
    private val workflowCiBuilder: WorkflowCiBuilder,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun estandarizar(ctx: TemplateRepoContext) {
        val ciWorkflowPath = ".github/workflows/ci.yml"
        val existingCiSha = ctx.archivosEnArbol[ciWorkflowPath]
            ?: if (!ctx.tieneArbol) ctx.obtenerArchivo(ciWorkflowPath)?.sha else null

        logger.info("Inyectando/pisando obligatoriamente {} en el template '{}' (.NET)", ciWorkflowPath, ctx.repoName)
        ctx.escribirArchivo(
            path = ciWorkflowPath,
            content = workflowCiBuilder.build(LenguajeProyecto.DOTNET),
            message = "ci: inyectar workflow estandarizado de calidad UNQlassroom para .NET (Roslyn & Coverlet)",
            knownSha = existingCiSha,
        )
    }
}
