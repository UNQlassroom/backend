package com.ar.edu.unq.unqlassroom

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.TimeZone

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
class UnqlassroomApplication

fun main(args: Array<String>) {
	TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"))
	runApplication<UnqlassroomApplication>(*args)
}
