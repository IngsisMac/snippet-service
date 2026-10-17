package ingsis.snippet.service.redis

import ingsis.snippet.redis.event.FormatResultEvent
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import ingsis.snippet.service.store.InMemorySnippetStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.stream.ObjectRecord
import org.springframework.data.redis.core.RedisTemplate
import java.util.Optional
import java.util.UUID

class FormatResultConsumerTest {
    private lateinit var statusRepository: SnippetStatusRepository
    private lateinit var snippetStore: InMemorySnippetStore
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var consumer: FormatResultConsumer

    @BeforeEach
    fun setUp() {
        statusRepository = mock()
        snippetStore = InMemorySnippetStore()
        redisTemplate = mock()
        consumer = FormatResultConsumer(statusRepository, snippetStore, redisTemplate)
    }

    @Test
    fun updatesContentAndStatusWhenRulesVersionIsGreaterOrEqual() {
        val snippetId = UUID.randomUUID()
        val current = SnippetStatus(snippetId = snippetId, formatRulesVersion = 2)
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(current))
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        val event =
            FormatResultEvent(
                snippetId = snippetId.toString(),
                rulesVersion = 3,
                formattedContent = "let formatted = true;",
                status = "SUCCESS",
            )
        val record = ObjectRecord.create("format-result", event)

        consumer.onMessage(record)

        assertEquals("let formatted = true;", snippetStore.get(snippetId))
        assertEquals(3, current.formatRulesVersion)
        verify(statusRepository).save(current)
    }

    @Test
    fun discardsObsoleteResultWhenRulesVersionIsLower() {
        val snippetId = UUID.randomUUID()
        val current = SnippetStatus(snippetId = snippetId, formatRulesVersion = 5)
        snippetStore.put(snippetId, "let existing = 1;")
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(current))

        val event =
            FormatResultEvent(
                snippetId = snippetId.toString(),
                rulesVersion = 4,
                formattedContent = "let oldFormatted = 1;",
                status = "SUCCESS",
            )
        val record = ObjectRecord.create("format-result", event)

        consumer.onMessage(record)

        assertEquals("let existing = 1;", snippetStore.get(snippetId))
        assertEquals(5, current.formatRulesVersion)
        verify(statusRepository, never()).save(any())
    }

    @Test
    fun preservesExistingContentWhenFormatFails() {
        val snippetId = UUID.randomUUID()
        val current = SnippetStatus(snippetId = snippetId, formatRulesVersion = 2)
        snippetStore.put(snippetId, "let untouched = 1;")
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(current))
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        val event =
            FormatResultEvent(
                snippetId = snippetId.toString(),
                rulesVersion = 3,
                formattedContent = "",
                status = "FAILED",
            )
        val record = ObjectRecord.create("format-result", event)

        consumer.onMessage(record)

        assertEquals("let untouched = 1;", snippetStore.get(snippetId))
        assertEquals(3, current.formatRulesVersion)
        verify(statusRepository).save(current)
    }

    @Test
    fun ignoresRecordWithInvalidSnippetId() {
        val event =
            FormatResultEvent(
                snippetId = "invalid-uuid",
                rulesVersion = 1,
                formattedContent = "test",
                status = "SUCCESS",
            )
        val record = ObjectRecord.create("format-result", event)

        consumer.onMessage(record)

        verify(statusRepository, never()).save(any())
    }

    @Test
    fun configuresStreamReceiverOptionsCorrectly() {
        val options = consumer.options()

        assertEquals(java.time.Duration.ofSeconds(10), options.pollTimeout)
        assertEquals(FormatResultEvent::class.java, options.targetType)
    }
}
