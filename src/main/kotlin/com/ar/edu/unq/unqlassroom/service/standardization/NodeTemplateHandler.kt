package com.ar.edu.unq.unqlassroom.service.standardization

import com.ar.edu.unq.unqlassroom.model.LenguajeProyecto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class NodeTemplateHandler(
    private val workflowCiBuilder: WorkflowCiBuilder,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun estandarizar(ctx: TemplateRepoContext) {
        // 1. Inyectar configuración de ESLint si el template no posee ninguna
        val tieneEslintConfig = if (ctx.tieneArbol) {
            ctx.archivosEnArbol.keys.any { path ->
                val p = path.lowercase()
                p == "eslint.config.js" || p == "eslint.config.mjs" || p == "eslint.config.cjs" ||
                    p == "eslint.config.ts" || p == ".eslintrc" || p.startsWith(".eslintrc.")
            }
        } else {
            ctx.existeArchivo("eslint.config.mjs") || ctx.existeArchivo("eslint.config.js") ||
                ctx.existeArchivo(".eslintrc.json") || ctx.existeArchivo(".eslintrc.js") ||
                ctx.existeArchivo(".eslintrc.yml") || ctx.existeArchivo(".eslintrc.yaml")
        }

        if (!tieneEslintConfig && !ctx.existeArchivo("eslint.config.mjs")) {
            logger.info("El template '{}' (Node.js) no posee configuración de ESLint; inyectando eslint.config.mjs", ctx.repoName)
            ctx.escribirArchivo(
                path = "eslint.config.mjs",
                content = StandardizationResourceLoader.load("node/eslint.config.mjs"),
                message = "chore(ci): agregar configuración base de ESLint en eslint.config.mjs",
            )
        }

        // 2. Inyectar test de rescate si no existen tests
        val tieneTests = if (ctx.tieneArbol) {
            ctx.archivosEnArbol.keys.any { path ->
                val p = path.lowercase()
                p.endsWith(".test.js") || p.endsWith(".spec.js") ||
                    p.endsWith(".test.ts") || p.endsWith(".spec.ts") ||
                    p.endsWith(".test.jsx") || p.endsWith(".test.tsx") ||
                    p.startsWith("test/") || p.startsWith("tests/") || p.startsWith("__tests__/")
            }
        } else {
            ctx.existeArchivo("test/sanity.test.js")
        }

        if (!tieneTests && !ctx.existeArchivo("test/sanity.test.js")) {
            logger.info("El template '{}' (Node.js) no posee tests; inyectando test/sanity.test.js", ctx.repoName)
            ctx.escribirArchivo(
                path = "test/sanity.test.js",
                content = StandardizationResourceLoader.load("node/sanity.test.js"),
                message = "test(ci): agregar test de rescate por defecto en test/sanity.test.js",
            )
        }

        // 3. Inyectar workflow de CI
        val ciWorkflowPath = ".github/workflows/ci.yml"
        val existingCiSha = ctx.archivosEnArbol[ciWorkflowPath]
            ?: if (!ctx.tieneArbol) ctx.obtenerArchivo(ciWorkflowPath)?.sha else null

        logger.info("Inyectando/pisando obligatoriamente {} en el template '{}' (Node.js)", ciWorkflowPath, ctx.repoName)
        ctx.escribirArchivo(
            path = ciWorkflowPath,
            content = workflowCiBuilder.build(LenguajeProyecto.NODE_JS),
            message = "ci: inyectar workflow estandarizado de calidad UNQlassroom para Node.js (ESLint & Tests)",
            knownSha = existingCiSha,
        )
    }
}
