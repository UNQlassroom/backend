package com.ar.edu.unq.unqlassroom.service.standardization

import com.ar.edu.unq.unqlassroom.model.LenguajeProyecto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PythonTemplateHandler(
    private val workflowCiBuilder: WorkflowCiBuilder,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun estandarizar(ctx: TemplateRepoContext) {
        val tieneTests = if (ctx.tieneArbol) {
            ctx.archivosEnArbol.keys.any { path ->
                val p = path.lowercase()
                (p.startsWith("test/") || p.startsWith("tests/") || p.contains("/test/") || p.contains("/tests/")) ||
                    p.substringAfterLast('/').let { it.startsWith("test_") || it.endsWith("_test.py") }
            }
        } else {
            ctx.existeArchivo("tests/test_sanity.py")
        }

        if (!tieneTests && !ctx.existeArchivo("tests/test_sanity.py")) {
            logger.info("El template '{}' (Python) no posee tests; inyectando tests/test_sanity.py", ctx.repoName)
            ctx.escribirArchivo(
                path = "tests/test_sanity.py",
                content = StandardizationResourceLoader.load("python/test_sanity.py"),
                message = "test(ci): agregar test de rescate por defecto en tests/test_sanity.py",
            )
        }

        val ciWorkflowPath = ".github/workflows/ci.yml"
        val existingCiSha = ctx.archivosEnArbol[ciWorkflowPath]
            ?: if (!ctx.tieneArbol) ctx.obtenerArchivo(ciWorkflowPath)?.sha else null

        logger.info("Inyectando/pisando obligatoriamente {} en el template '{}' (Python)", ciWorkflowPath, ctx.repoName)
        ctx.escribirArchivo(
            path = ciWorkflowPath,
            content = workflowCiBuilder.build(LenguajeProyecto.PYTHON),
            message = "ci: inyectar workflow estandarizado de calidad UNQlassroom para Python (Ruff & Pytest)",
            knownSha = existingCiSha,
        )
    }
}
