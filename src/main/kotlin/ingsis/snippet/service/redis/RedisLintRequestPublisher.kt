package ingsis.snippet.service.redis

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RedisLintRequestPublisher(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) : LintRequestPublisher {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun publishLintRequest(
        snippetId: UUID,
        content: String,
        version: String,
        rulesVersion: Int,
        rules: Map<String, Any>,
    ) {
        val payload =
            mapOf(
                "snippetId" to snippetId.toString(),
                "content" to content,
                "version" to version,
                "rulesVersion" to rulesVersion.toString(),
                "rules" to objectMapper.writeValueAsString(rules),
                "timestamp" to System.currentTimeMillis().toString(),
            )
        val record: MapRecord<String, String, String> =
            StreamRecords
                .newRecord()
                .ofStrings(payload)
                .withStreamKey(LINT_REQUEST_STREAM)
        redisTemplate.opsForStream<String, String>().add(record)
        logger.info("Published lint request for snippet {} v{}", snippetId, rulesVersion)
    }

    companion object {
        const val LINT_REQUEST_STREAM = "lint-requests-stream"
    }
}
