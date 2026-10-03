package ingsis.snippet.service.redis

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.client.fake.FakeLintRequestPublisher
import ingsis.snippet.service.client.fake.FakePermissionClient
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
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.stream.MapRecord
import java.util.Optional
import java.util.UUID

class RuleChangeConsumerTest {
    private lateinit var snippetRepository: SnippetRepository
    private lateinit var statusRepository: SnippetStatusRepository
    private lateinit var snippetStore: InMemorySnippetStore
    private lateinit var fakePermissionClient: FakePermissionClient
    private lateinit var fakePublisher: FakeLintRequestPublisher
    private lateinit var consumer: RuleChangeConsumer
    private val objectMapper = ObjectMapper()
    private val userId = "auth0|user1"

    @BeforeEach
    fun setUp() {
        snippetRepository = mock()
        statusRepository = mock()
        snippetStore = InMemorySnippetStore()
        fakePermissionClient = FakePermissionClient()
        fakePublisher = FakeLintRequestPublisher()
        consumer =
            RuleChangeConsumer(
                snippetRepository = snippetRepository,
                statusRepository = statusRepository,
                snippetStore = snippetStore,
                permissionClient = fakePermissionClient,
                lintRequestPublisher = fakePublisher,
                objectMapper = objectMapper,
            )
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }
    }

    private fun ownedSnippet(name: String): Snippet {
        val snippet = Snippet(name = name, ownerId = userId, language = "printscript", version = "1.1")
        fakePermissionClient.setPermission(snippet.id, userId, PermissionLevel.OWNER)
        return snippet
    }

    private fun ruleChange(
        rulesVersion: Int,
        rules: String = "{}",
    ): MapRecord<String, String, String> =
        MapRecord.create(
            "lint-rules-stream",
            mapOf("userId" to userId, "rulesVersion" to rulesVersion.toString(), "rules" to rules),
        )

    @Test
    fun shouldPublishOneLintRequestPerOwnedSnippetWithContentFromStore() {
        val snippet = ownedSnippet("My Snippet")
        val status = SnippetStatus(snippetId = snippet.id, status = ComplianceStatus.COMPLIANT, rulesVersion = 1)
        snippetStore.put(snippet.id, "let x = 1;")
        whenever(snippetRepository.findAllById(argThat<Iterable<UUID>> { toList() == listOf(snippet.id) }))
            .thenReturn(listOf(snippet))
        whenever(statusRepository.findById(snippet.id)).thenReturn(Optional.of(status))

        consumer.processLintRuleChange(ruleChange(2, "{\"identifier_format\":\"camelCase\"}"))

        assertEquals(1, fakePublisher.requests.size)
        val request = fakePublisher.requests[0]
        assertEquals(snippet.id, request.snippetId)
        assertEquals("let x = 1;", request.content)
        assertEquals(2, request.rulesVersion)
        assertEquals("camelCase", request.rules["identifier_format"])
        assertEquals(ComplianceStatus.PENDING, status.status)
        assertEquals(2, status.rulesVersion)
    }

    @Test
    fun shouldIgnoreSnippetsTheUserOnlyHasSharedAccessTo() {
        val shared = Snippet(name = "Shared", ownerId = "auth0|other", language = "printscript", version = "1.1")
        fakePermissionClient.setPermission(shared.id, userId, PermissionLevel.WRITE)
        whenever(snippetRepository.findAllById(argThat<Iterable<UUID>> { toList().isEmpty() })).thenReturn(emptyList())

        consumer.processLintRuleChange(ruleChange(1))

        assertEquals(0, fakePublisher.requests.size)
    }

    @Test
    fun shouldMarkSnippetFailedWhenContentIsMissingAndContinueWithOthers() {
        val orphan = ownedSnippet("Orphan")
        val healthy = ownedSnippet("Healthy")
        val orphanStatus = SnippetStatus(snippetId = orphan.id)
        snippetStore.put(healthy.id, "println(1);")
        whenever(snippetRepository.findAllById(any<Iterable<UUID>>())).thenReturn(listOf(orphan, healthy))
        whenever(statusRepository.findById(orphan.id)).thenReturn(Optional.of(orphanStatus))
        whenever(statusRepository.findById(healthy.id)).thenReturn(Optional.of(SnippetStatus(snippetId = healthy.id)))

        consumer.processLintRuleChange(ruleChange(3))

        assertEquals(1, fakePublisher.requests.size)
        assertEquals(healthy.id, fakePublisher.requests[0].snippetId)
        assertEquals(ComplianceStatus.FAILED, orphanStatus.status)
    }

    @Test
    fun shouldTolerateFailureInOneSnippetAndContinueProcessingOthers() {
        val broken = ownedSnippet("Broken")
        val healthy = ownedSnippet("Healthy")
        snippetStore.put(broken.id, "code 1")
        snippetStore.put(healthy.id, "code 2")
        whenever(snippetRepository.findAllById(any<Iterable<UUID>>())).thenReturn(listOf(broken, healthy))
        whenever(statusRepository.findById(broken.id)).thenThrow(RuntimeException("Status DB failure"))
        whenever(statusRepository.findById(healthy.id)).thenReturn(Optional.of(SnippetStatus(snippetId = healthy.id)))

        consumer.processLintRuleChange(ruleChange(3))

        assertEquals(1, fakePublisher.requests.size)
        assertEquals(healthy.id, fakePublisher.requests[0].snippetId)
    }

    @Test
    fun shouldIgnoreRecordWithoutUserId() {
        val record = MapRecord.create("lint-rules-stream", mapOf("rulesVersion" to "1"))

        consumer.processLintRuleChange(record)

        assertEquals(0, fakePublisher.requests.size)
    }
}
