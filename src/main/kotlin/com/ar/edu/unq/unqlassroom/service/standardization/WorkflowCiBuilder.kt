package com.ar.edu.unq.unqlassroom.service.standardization

import com.ar.edu.unq.unqlassroom.model.LenguajeProyecto
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class WorkflowCiBuilder(
    @Value("\${github.webhook.public-url:}")
    private val webhookPublicUrl: String = "",
) {
    val targetWebhookUrl: String
        get() = webhookPublicUrl.trim()

    fun build(lenguaje: LenguajeProyecto): String {
        val placeholders = mapOf("TARGET_WEBHOOK_URL" to targetWebhookUrl)
        val templatePath = when (lenguaje) {
            LenguajeProyecto.JAVA_MAVEN -> "workflows/ci-java-maven.yml"
            LenguajeProyecto.JAVA_GRADLE -> "workflows/ci-java-gradle.yml"
            LenguajeProyecto.PYTHON -> "workflows/ci-python.yml"
            LenguajeProyecto.NODE_JS -> "workflows/ci-node.yml"
            LenguajeProyecto.DOTNET -> "workflows/ci-dotnet.yml"
            LenguajeProyecto.GENERICO -> "workflows/ci-java-maven.yml"
        }
        return StandardizationResourceLoader.loadWithPlaceholders(templatePath, placeholders)
    }

    fun buildJava(esGradle: Boolean): String {
        val placeholders = mapOf("TARGET_WEBHOOK_URL" to targetWebhookUrl)
        val templatePath = if (esGradle) "workflows/ci-java-gradle.yml" else "workflows/ci-java-maven.yml"
        return StandardizationResourceLoader.loadWithPlaceholders(templatePath, placeholders)
    }
}
