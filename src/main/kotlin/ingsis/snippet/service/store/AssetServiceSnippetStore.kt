package ingsis.snippet.service.store

import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.util.UUID

class AssetServiceSnippetStore(
    private val restClient: RestClient,
) : SnippetStore {
    override fun get(snippetId: UUID): String? =
        try {
            restClient
                .get()
                .uri("/v1/asset/snippets/{snippetId}", snippetId)
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
            .post()
            .uri("/v1/asset/snippets/{snippetId}", snippetId)
            .contentType(MediaType.APPLICATION_JSON)
            .body(SaveAssetPayload(content = content))
            .retrieve()
            .toBodilessEntity()
    }

    override fun delete(snippetId: UUID) {
        try {
            restClient
                .delete()
                .uri("/v1/asset/snippets/{snippetId}", snippetId)
                .retrieve()
                .toBodilessEntity()
        } catch (_: HttpClientErrorException.NotFound) {
            // Idempotent delete if resource is not found
        }
    }

    private data class SaveAssetPayload(
        val content: String,
    )
}
