package ingsis.snippet.service.domain.repository

import ingsis.snippet.service.domain.model.TestCase
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TestCaseRepository : JpaRepository<TestCase, UUID> {
    fun findAllBySnippetId(snippetId: UUID): List<TestCase>

    fun findBySnippetIdAndId(
        snippetId: UUID,
        id: UUID,
    ): java.util.Optional<TestCase>

    fun deleteBySnippetIdAndId(
        snippetId: UUID,
        id: UUID,
    )
}
