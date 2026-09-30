package ingsis.snippet.service.config

import ingsis.snippet.service.client.HttpPermissionClient
import ingsis.snippet.service.client.HttpRunnerClient
import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.client.RunnerClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class ClientConfig {
    @Bean
    fun runnerClient(
        @Value("\${services.runner.url}") runnerUrl: String,
        builder: RestClient.Builder,
    ): RunnerClient {
        val restClient = builder.baseUrl(runnerUrl).build()
        return HttpRunnerClient(restClient)
    }

    @Bean
    fun permissionClient(
        @Value("\${services.permission.url}") permissionUrl: String,
        builder: RestClient.Builder,
    ): PermissionClient {
        val restClient = builder.baseUrl(permissionUrl).build()
        return HttpPermissionClient(restClient)
    }
}
