package ingsis.snippet.service.domain.repository

import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.domain.model.Snippet
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Los parámetros opcionales van con `CAST(... AS string)`: cuando llegan en `null`, PostgreSQL no
 * puede inferir el tipo del bind y falla con `function lower(bytea) does not exist`.
 */
@Repository
interface SnippetRepository : JpaRepository<Snippet, UUID> {
    @Query(
        """
        SELECT s FROM Snippet s
        LEFT JOIN SnippetStatus st ON st.snippetId = s.id
        WHERE s.id IN :ids
        AND (CAST(:name AS string) IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:name AS string), '%')))
        AND (CAST(:language AS string) IS NULL OR LOWER(s.language) = LOWER(CAST(:language AS string)))
        AND (:status IS NULL OR st.status = :status)
        """,
    )
    fun findAllByIdsAndFilters(
        @Param("ids") ids: Collection<UUID>,
        @Param("name") name: String?,
        @Param("language") language: String?,
        @Param("status") status: ComplianceStatus?,
        pageable: Pageable,
    ): Page<Snippet>
}
