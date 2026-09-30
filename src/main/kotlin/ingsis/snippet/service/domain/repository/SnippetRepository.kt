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

    @Query(
        """
        SELECT s FROM Snippet s
        LEFT JOIN SnippetStatus st ON st.snippetId = s.id
        WHERE s.ownerId = :ownerId
        AND (:name IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%')))
        AND (:language IS NULL OR LOWER(s.language) = LOWER(:language))
        AND (:status IS NULL OR st.status = :status)
        """,
    )
    fun findAllByOwnerAndFilters(
        @Param("ownerId") ownerId: String,
        @Param("name") name: String?,
        @Param("language") language: String?,
        @Param("status") status: ComplianceStatus?,
        pageable: Pageable,
    ): Page<Snippet>

    @Query(
        """
        SELECT s FROM Snippet s
        LEFT JOIN SnippetStatus st ON st.snippetId = s.id
        WHERE s.id IN :ids
        AND (:name IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%')))
        AND (:language IS NULL OR LOWER(s.language) = LOWER(:language))
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

    @Query(
        """
        SELECT s FROM Snippet s
        LEFT JOIN SnippetStatus st ON st.snippetId = s.id
        WHERE (s.ownerId = :ownerId OR s.id IN :ids)
        AND (:name IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :name, '%')))
        AND (:language IS NULL OR LOWER(s.language) = LOWER(:language))
        AND (:status IS NULL OR st.status = :status)
        """,
    )
    @Suppress("LongParameterList")
    fun findAllByOwnerOrIdsAndFilters(
        @Param("ownerId") ownerId: String,
        @Param("ids") ids: Collection<UUID>,
        @Param("name") name: String?,
        @Param("language") language: String?,
        @Param("status") status: ComplianceStatus?,
        pageable: Pageable,
    ): Page<Snippet>
}
