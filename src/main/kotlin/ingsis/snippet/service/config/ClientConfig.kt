package ingsis.snippet.service.config

import ingsis.snippet.service.client.HttpPermissionClient
import ingsis.snippet.service.client.HttpRunnerClient
import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.client.RunnerClient
import ingsis.snippet.service.client.auth.Auth0ClientCredentialsTokenProvider
import ingsis.snippet.service.client.auth.BearerTokenInterceptor
import ingsis.snippet.service.client.auth.M2mProperties
import ingsis.snippet.service.client.auth.ServiceTokenProvider
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * Cada cliente saliente tiene timeouts explícitos: sin ellos un servicio colgado bloquea
 * los hilos de Tomcat de `snippet-service` indefinidamente. Los valores viven en
 * `services.<nombre>.connect-timeout` / `read-timeout` y pueden ajustarse por entorno.
 *
 * Las llamadas a `permission-service` y `runner-service` llevan el token M2M de Auth0 cuando
 * `auth.m2m.enabled=true`; el asset-service de la cátedra no valida tokens, así que no lo recibe.
 */
@Configuration
@EnableConfigurationProperties(ServicesProperties::class, M2mProperties::class)
class ClientConfig {
    @Bean
    fun serviceTokenProvider(
        properties: M2mProperties,
        builder: RestClient.Builder,
    ): ServiceTokenProvider {
        if (!properties.enabled) return ServiceTokenProvider.NONE
        return Auth0ClientCredentialsTokenProvider(builder.clone().build(), properties)
    }

    @Bean
    fun runnerClient(
        properties: ServicesProperties,
        tokenProvider: ServiceTokenProvider,
        builder: RestClient.Builder,
    ): RunnerClient = HttpRunnerClient(restClientFor(properties.runner, builder, BearerTokenInterceptor(tokenProvider)))

    @Bean
    fun permissionClient(
        properties: ServicesProperties,
        tokenProvider: ServiceTokenProvider,
        builder: RestClient.Builder,
    ): PermissionClient =
        HttpPermissionClient(restClientFor(properties.permission, builder, BearerTokenInterceptor(tokenProvider)))

    companion object {
        fun restClientFor(
            target: ServiceTarget,
            builder: RestClient.Builder,
            vararg interceptors: ClientHttpRequestInterceptor,
        ): RestClient =
            builder
                .clone()
                .baseUrl(target.url)
                .requestFactory(requestFactory(target))
                .requestInterceptors { it.addAll(interceptors) }
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
