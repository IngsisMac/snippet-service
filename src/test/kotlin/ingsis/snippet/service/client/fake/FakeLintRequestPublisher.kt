package ingsis.snippet.service.client.fake

import ingsis.snippet.service.redis.LintRequestPublisher
import java.util.UUID

data class LintRequestRecord(
    val snippetId: UUID,
    val content: String,
    val version: String,
    val rulesVersion: Int,
    val rules: Map<String, Any>,
)

class FakeLintRequestPublisher : LintRequestPublisher {
    val requests = mutableListOf<LintRequestRecord>()

    override fun publishLintRequest(
        snippetId: UUID,
        content: String,
        version: String,
        rulesVersion: Int,
        rules: Map<String, Any>,
    ) {
        requests.add(
            LintRequestRecord(
                snippetId = snippetId,
                content = content,
                version = version,
                rulesVersion = rulesVersion,
                rules = rules,
            )
        )
    }

    fun clear() {
        requests.clear()
    }
}
