package ingsis.snippet.service.domain.repository

import ingsis.snippet.service.domain.model.Snippet
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SnippetRepository : JpaRepository<Snippet, UUID> {
    fun findAllByOwnerId(
        ownerId: String,
        pageable: Pageable,
    ): Page<Snippet>

    fun findAllByIdIn(
        ids: List<UUID>,
        pageable: Pageable,
    ): Page<Snippet>
}
