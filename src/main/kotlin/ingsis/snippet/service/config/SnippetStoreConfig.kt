package ingsis.snippet.service.config

import ingsis.snippet.service.store.AssetServiceSnippetStore
import ingsis.snippet.service.store.InMemorySnippetStore
import ingsis.snippet.service.store.SnippetStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class SnippetStoreConfig {
    @Bean
    @ConditionalOnProperty(name = ["snippet.store.type"], havingValue = "asset-service", matchIfMissing = true)
    fun assetServiceSnippetStore(
        @Value("\${services.asset.url:http://localhost:8083}") assetUrl: String,
        builder: RestClient.Builder,
    ): SnippetStore {
        val restClient = builder.baseUrl(assetUrl).build()
        return AssetServiceSnippetStore(restClient)
    }

    @Bean
    @ConditionalOnProperty(name = ["snippet.store.type"], havingValue = "memory")
    fun inMemorySnippetStore(): SnippetStore = InMemorySnippetStore()
}
