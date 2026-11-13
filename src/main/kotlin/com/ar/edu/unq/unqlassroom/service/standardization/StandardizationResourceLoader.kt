package com.ar.edu.unq.unqlassroom.service.standardization

import org.springframework.core.io.ClassPathResource
import java.nio.charset.StandardCharsets

object StandardizationResourceLoader {

    fun load(relativePath: String): String {
        val cleanPath = "templates/standardization/" + relativePath.trimStart('/')
        val resource = ClassPathResource(cleanPath)
        return resource.inputStream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
    }

    fun loadWithPlaceholders(relativePath: String, placeholders: Map<String, String>): String {
        var content = load(relativePath)
        placeholders.forEach { (key, value) ->
            content = content.replace("{{$key}}", value)
        }
        return content
    }
}
