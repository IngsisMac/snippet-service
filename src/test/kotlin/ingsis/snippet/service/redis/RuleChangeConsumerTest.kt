package ingsis.snippet.service.redis

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.client.fake.FakeLintRequestPublisher
import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
import ingsis.snippet.service.store.InMemorySnippetStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.data.redis.connection.stream.MapRecord
import java.util.Optional
import java.util.UUID

class RuleChangeConsumerTest {
    private lateinit var snippetRepository: SnippetRepository
    private lateinit var statusRepository: SnippetStatusRepository
    private lateinit var snippetStore: InMemorySnippetStore
    private lateinit var fakePublisher: FakeLintRequestPublisher
    private lateinit var consumer: RuleChangeConsumer
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        snippetRepository = mock()
        statusRepository = mock()
        snippetStore = InMemorySnippetStore()
        fakePublisher = FakeLintRequestPublisher()
        consumer =
            RuleChangeConsumer(
                snippetRepository = snippetRepository,
                statusRepository = statusRepository,
                snippetStore = snippetStore,
                lintRequestPublisher = fakePublisher,
                objectMapper = objectMapper,
            )
    }

    @Test
    fun shouldProcessLintRuleChangeAndPublishRequestsForUserSnippets() {
        val userId = "auth0|user1"
        val snippetId = UUID.randomUUID()
        val snippet =
            Snippet(
                id = snippetId,
                name = "My Snippet",
                ownerId = userId,
                language = "printscript",
                version = "1.1",
                content = "let x = 1;",
            )
        val status = SnippetStatus(snippetId = snippetId, status = ComplianceStatus.COMPLIANT, rulesVersion = 1)

        whenever(snippetRepository.findAllByOwnerId(userId, Pageable.unpaged())).thenReturn(PageImpl(listOf(snippet)))
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(status))
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        val payload =
            mapOf(
                "userId" to userId,
                "rulesVersion" to "2",
                "rules" to "{\"identifier_format\":\"camelCase\"}",
            )
        val record = MapRecord.create("lint-rules-stream", payload)

        consumer.processLintRuleChange(record)

        assertEquals(1, fakePublisher.requests.size)
        val req = fakePublisher.requests[0]
        assertEquals(snippetId, req.snippetId)
        assertEquals("let x = 1;", req.content)
        assertEquals(2, req.rulesVersion)
        assertEquals("camelCase", req.rules["identifier_format"])
        assertEquals(ComplianceStatus.PENDING, status.status)
        assertEquals(2, status.rulesVersion)
    }

    @Test
    fun shouldTolerateFailureInOneSnippetAndContinueProcessingOthers() {
        val userId = "auth0|user2"
        val snippet1Id = UUID.randomUUID()
        val snippet2Id = UUID.randomUUID()
        val snippet1 =
            Snippet(
                id = snippet1Id,
                name = "S1",
                ownerId = userId,
                language = "printscript",
                version = "1.1",
                content = "code 1",
            )
        val snippet2 =
            Snippet(
                id = snippet2Id,
                name = "S2",
                ownerId = userId,
                language = "printscript",
                version = "1.1",
                content = "code 2",
            )

        whenever(snippetRepository.findAllByOwnerId(userId, Pageable.unpaged()))
            .thenReturn(PageImpl(listOf(snippet1, snippet2)))
        whenever(statusRepository.findById(snippet1Id)).thenThrow(RuntimeException("Status DB failure"))
        whenever(statusRepository.findById(snippet2Id)).thenReturn(Optional.of(SnippetStatus(snippetId = snippet2Id)))
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        val payload =
            mapOf(
                "userId" to userId,
                "rulesVersion" to "3",
                "rules" to "{}",
            )
        val record = MapRecord.create("lint-rules-stream", payload)

        consumer.processLintRuleChange(record)

        // snippet1 failed gracefully, snippet2 was processed successfully
        assertEquals(1, fakePublisher.requests.size)
        assertEquals(snippet2Id, fakePublisher.requests[0].snippetId)
    }

    @Test
    fun shouldIgnoreRecordWithoutUserId() {
        val payload = mapOf("rulesVersion" to "1")
        val record = MapRecord.create("lint-rules-stream", payload)

        consumer.processLintRuleChange(record)

        assertEquals(0, fakePublisher.requests.size)
    }
}
