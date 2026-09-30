package ingsis.snippet.service.redis

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.StreamOperations
import org.springframework.data.redis.core.StringRedisTemplate
import java.util.UUID

class RedisLintRequestPublisherTest {
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var streamOperations: StreamOperations<String, String, String>
    private lateinit var publisher: RedisLintRequestPublisher
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        redisTemplate = mock()
        streamOperations = mock()
        whenever(redisTemplate.opsForStream<String, String>()).thenReturn(streamOperations)
        publisher = RedisLintRequestPublisher(redisTemplate, objectMapper)
    }

    @Test
    fun shouldPublishLintRequestToStream() {
        whenever(streamOperations.add(any())).thenReturn(RecordId.of("2000-0"))

        publisher.publishLintRequest(
            snippetId = UUID.randomUUID(),
            content = "let a: number = 1;",
            version = "1.1",
            rulesVersion = 3,
            rules = mapOf("camelCase" to true),
        )

        verify(streamOperations).add(any())
    }
}
