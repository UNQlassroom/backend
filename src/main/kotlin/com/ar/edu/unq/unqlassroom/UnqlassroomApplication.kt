package com.ar.edu.unq.unqlassroom

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import java.nio.file.Files
import java.nio.file.Path

@SpringBootApplication
@ConfigurationPropertiesScan
class UnqlassroomApplication

fun main(args: Array<String>) {
	loadDotEnv(Path.of(".env"))
	runApplication<UnqlassroomApplication>(*args)
}

private fun loadDotEnv(dotEnvPath: Path) {
	if (!Files.exists(dotEnvPath)) {
		return
	}

	Files.readAllLines(dotEnvPath)
		.map { it.trim() }
		.filter { it.isNotEmpty() && !it.startsWith("#") }
		.forEach { line ->
			val separatorIndex = line.indexOf('=')
			if (separatorIndex <= 0) {
				return@forEach
			}

			val key = line.substring(0, separatorIndex).trim()
			val value = line.substring(separatorIndex + 1).trim()
			if (System.getProperty(key).isNullOrBlank()) {
				System.setProperty(key, value)
			}
		}
}
