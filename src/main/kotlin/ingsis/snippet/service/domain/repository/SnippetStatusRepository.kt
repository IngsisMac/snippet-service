package ingsis.snippet.service.domain.repository

import ingsis.snippet.service.domain.model.SnippetStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SnippetStatusRepository : JpaRepository<SnippetStatus, UUID>
