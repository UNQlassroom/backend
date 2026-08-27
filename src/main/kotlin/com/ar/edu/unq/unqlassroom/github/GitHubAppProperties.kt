package com.ar.edu.unq.unqlassroom.github

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "github.app")
data class GitHubAppProperties(
    val appId: String = "",
    val installationId: String = "",
    val privateKeyPath: String = "",
    val apiBaseUrl: String = "https://api.github.com",
)