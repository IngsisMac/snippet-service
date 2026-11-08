package ingsis.snippet.service.domain.repository

import ingsis.snippet.service.domain.model.TestCaseResult
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TestCaseResultRepository : JpaRepository<TestCaseResult, UUID> {
    fun findAllBySnippetId(snippetId: UUID): List<TestCaseResult>
}
