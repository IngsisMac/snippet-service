package ingsis.snippet.service.client.auth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth.m2m")
data class M2mProperties(
    val enabled: Boolean = false,
    val tokenUri: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val audience: String = "",
)
