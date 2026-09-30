package ingsis.snippet.service.redis

import java.util.UUID

interface LintRequestPublisher {
    fun publishLintRequest(
        snippetId: UUID,
        content: String,
        version: String,
        rulesVersion: Int,
        rules: Map<String, Any>,
    )
}
