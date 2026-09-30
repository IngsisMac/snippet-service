package ingsis.snippet.service.store

import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemorySnippetStore : SnippetStore {
    private val storage = ConcurrentHashMap<UUID, String>()

    override fun get(snippetId: UUID): String? = storage[snippetId]

    override fun put(
        snippetId: UUID,
        content: String,
    ) {
        storage[snippetId] = content
    }

    override fun delete(snippetId: UUID) {
        storage.remove(snippetId)
    }

    fun clear() {
        storage.clear()
    }
}
