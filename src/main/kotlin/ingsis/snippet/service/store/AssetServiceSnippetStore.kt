package ingsis.snippet.service.store

import org.springframework.http.MediaType
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.util.UUID

/**
 * Adaptador al asset-service provisto por la cátedra (`ghcr.io/austral-ingsis/snippet-asset-service`):
 * `PUT|GET|DELETE /v1/asset/{container}/{key}` con el contenido crudo en el body.
 * El contenedor es fijo (`snippets`) y la clave se deriva del `snippetId`.
 */
class AssetServiceSnippetStore(
    private val restClient: RestClient,
    private val container: String = DEFAULT_CONTAINER,
) : SnippetStore {
    override fun get(snippetId: UUID): String? =
        try {
            restClient
                .get()
                .uri("/v1/asset/{container}/{key}", container, snippetId)
                .accept(MediaType.ALL)
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
            // Borrado idempotente: si el blob ya no existe no hay nada que deshacer
        }
    }

    companion object {
        const val DEFAULT_CONTAINER = "snippets"
    }
}
