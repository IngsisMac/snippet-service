package ingsis.snippet.service.config

import ingsis.snippet.service.store.AssetServiceSnippetStore
import ingsis.snippet.service.store.InMemorySnippetStore
import ingsis.snippet.service.store.SnippetStore
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class SnippetStoreConfig {
    @Bean
    @ConditionalOnProperty(name = ["snippet.store.type"], havingValue = "asset-service", matchIfMissing = true)
    fun assetServiceSnippetStore(
        properties: ServicesProperties,
        builder: RestClient.Builder,
    ): SnippetStore = AssetServiceSnippetStore(ClientConfig.restClientFor(properties.asset, builder))

    @Bean
    @ConditionalOnProperty(name = ["snippet.store.type"], havingValue = "memory")
    fun inMemorySnippetStore(): SnippetStore = InMemorySnippetStore()
}
