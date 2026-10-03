package ingsis.snippet.service.store

import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.util.UUID

class AssetServiceSnippetStore(
    private val restClient: RestClient,
    private val container: String = "snippets",
) : SnippetStore {
    override fun get(snippetId: UUID): String? =
        try {
            restClient
                .get()
                .uri("/v1/asset/{container}/{key}", container, snippetId)
                .accept(MediaType.TEXT_PLAIN)
                .retrieve()
                .body(String::class.java)
        } catch (_: HttpClientErrorException.NotFound) {
            null
        }

    override fun put(
        snippetId: UUID,
        content: String,
    ) {
        restClient
            .put()
            .uri("/v1/asset/{container}/{key}", container, snippetId)
            .contentType(MediaType.TEXT_PLAIN)
            .body(content)
            .retrieve()
            .toBodilessEntity()
    }

    override fun delete(snippetId: UUID) {
        try {
            restClient
                .delete()
                .uri("/v1/asset/{container}/{key}", container, snippetId)
                .retrieve()
                .toBodilessEntity()
        } catch (_: HttpClientErrorException.NotFound) {
            // Idempotent delete if resource is not found
        }
    }
}
