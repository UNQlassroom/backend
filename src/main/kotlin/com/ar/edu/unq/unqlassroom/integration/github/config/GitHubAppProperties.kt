package com.ar.edu.unq.unqlassroom.integration.github.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "github.app")
data class GitHubAppProperties(
    val appId: String = "",
    val installationId: String = "",
    val privateKeyPath: String = "",
    val apiBaseUrl: String = "https://api.github.com",
    val organization: String = "",
)