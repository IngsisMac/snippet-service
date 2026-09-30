package ingsis.snippet.service.store

import java.util.UUID

interface SnippetStore {
    fun get(snippetId: UUID): String?

    fun put(
        snippetId: UUID,
        content: String,
    )

    fun delete(snippetId: UUID)
}
