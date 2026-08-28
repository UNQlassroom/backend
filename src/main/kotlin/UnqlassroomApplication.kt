package com.ar.edu.unq.unqlassroom

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import java.util.TimeZone

@SpringBootApplication
@ConfigurationPropertiesScan
class UnqlassroomApplication

fun main(args: Array<String>) {
	TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"))
	runApplication<UnqlassroomApplication>(*args)
}
