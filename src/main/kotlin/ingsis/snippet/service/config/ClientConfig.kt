package ingsis.snippet.service.config

import ingsis.snippet.service.client.HttpPermissionClient
import ingsis.snippet.service.client.HttpRunnerClient
import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.client.RunnerClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Cada cliente saliente tiene timeouts explícitos: sin ellos un servicio colgado bloquea
 * los hilos de Tomcat de `snippet-service` indefinidamente. Los valores viven en
 * `services.<nombre>.connect-timeout` / `read-timeout` y pueden ajustarse por entorno.
 */
@Configuration
@EnableConfigurationProperties(ServicesProperties::class)
class ClientConfig {
    @Bean
    fun runnerClient(
        properties: ServicesProperties,
        builder: RestClient.Builder,
    ): RunnerClient = HttpRunnerClient(restClientFor(properties.runner, builder))

    @Bean
    fun permissionClient(
        properties: ServicesProperties,
        builder: RestClient.Builder,
    ): PermissionClient = HttpPermissionClient(restClientFor(properties.permission, builder))

    companion object {
        fun restClientFor(
            target: ServiceTarget,
            builder: RestClient.Builder,
        ): RestClient =
            builder
                .baseUrl(target.url)
                .requestFactory(requestFactory(target))
                .build()

        private fun requestFactory(target: ServiceTarget): SimpleClientHttpRequestFactory =
            SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(target.connectTimeout)
                setReadTimeout(target.readTimeout)
            }
    }
}

@ConfigurationProperties(prefix = "services")
data class ServicesProperties(
    val runner: ServiceTarget,
    val permission: ServiceTarget,
    val asset: ServiceTarget,
)

data class ServiceTarget(
    val url: String,
    val connectTimeout: Duration = DEFAULT_CONNECT_TIMEOUT,
    val readTimeout: Duration = DEFAULT_READ_TIMEOUT,
) {
    companion object {
        val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
        val DEFAULT_READ_TIMEOUT: Duration = Duration.ofSeconds(3)
    }
}
