package ingsis.snippet.service.redis

import ingsis.snippet.redis.event.LintRequestEvent
import ingsis.snippet.redis.event.LintRulesChangeEvent
import ingsis.snippet.service.client.dto.PermissionLevel
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.stream.ObjectRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StreamOperations
import java.util.Optional
import java.util.UUID

class RuleChangeConsumerTest {
    private lateinit var snippetRepository: SnippetRepository
    private lateinit var statusRepository: SnippetStatusRepository
    private lateinit var snippetStore: InMemorySnippetStore
    private lateinit var fakePermissionClient: FakePermissionClient
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var producerRedisTemplate: RedisTemplate<String, String>
    private lateinit var streamOps: StreamOperations<String, String, Any>
    private lateinit var producer: LintRequestProducer
    private lateinit var consumer: RuleChangeConsumer
    private val userId = "auth0|user1"

    @BeforeEach
    fun setUp() {
        snippetRepository = mock()
        statusRepository = mock()
        snippetStore = InMemorySnippetStore()
        fakePermissionClient = FakePermissionClient()
        redisTemplate = mock()
        producerRedisTemplate = mock()
        streamOps = mock()

        whenever(producerRedisTemplate.opsForStream<String, Any>()).thenReturn(streamOps)
        whenever(streamOps.add(any())).thenReturn(RecordId.of("1-0"))
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        producer = LintRequestProducer(producerRedisTemplate)
        consumer =
            RuleChangeConsumer(
                snippetRepository = snippetRepository,
                statusRepository = statusRepository,
                snippetStore = snippetStore,
                permissionClient = fakePermissionClient,
                lintRequestProducer = producer,
                redisTemplate = redisTemplate,
                work = StreamWorkQueue.direct("lint-rules"),
            )
    }

    private fun ownedSnippet(name: String): Snippet {
        val snippet = Snippet(name = name, ownerId = userId, language = "printscript", version = "1.1")
        fakePermissionClient.setPermission(snippet.id, userId, PermissionLevel.OWNER)
        return snippet
    }

    private fun ruleChangeEvent(
        rulesVersion: Int,
        rules: String = "{}",
    ): ObjectRecord<String, LintRulesChangeEvent> =
        ObjectRecord.create(
            "lint-rules",
            LintRulesChangeEvent(userId = userId, rulesVersion = rulesVersion, rules = rules),
        )

    @Test
    fun publishesOneLintRequestPerOwnedSnippetWithContentFromStore() {
        val snippet = ownedSnippet("My Snippet")
        val status = SnippetStatus(snippetId = snippet.id, status = ComplianceStatus.COMPLIANT, rulesVersion = 1)
        snippetStore.put(snippet.id, "let x = 1;")
        whenever(snippetRepository.findAllById(argThat<Iterable<UUID>> { toList() == listOf(snippet.id) }))
            .thenReturn(listOf(snippet))
        whenever(statusRepository.findById(snippet.id)).thenReturn(Optional.of(status))

        consumer.onMessage(ruleChangeEvent(2, "{\"identifier_format\":\"camelCase\"}"))

        verify(streamOps).add(
            argThat<ObjectRecord<String, LintRequestEvent>> {
                value.snippetId == snippet.id.toString() &&
                    value.content == "let x = 1;" &&
                    value.rulesVersion == 2 &&
                    value.rules == "{\"identifier_format\":\"camelCase\"}"
            },
        )
        assertEquals(ComplianceStatus.PENDING, status.status)
        assertEquals(2, status.rulesVersion)
    }

    @Test
    fun ignoresSnippetsTheUserOnlyHasSharedAccessTo() {
        val shared = Snippet(name = "Shared", ownerId = "auth0|other", language = "printscript", version = "1.1")
        fakePermissionClient.setPermission(shared.id, userId, PermissionLevel.WRITE)
        whenever(snippetRepository.findAllById(argThat<Iterable<UUID>> { toList().isEmpty() })).thenReturn(emptyList())

        consumer.onMessage(ruleChangeEvent(1))

        verify(streamOps, never()).add(any())
    }

    @Test
    fun marksSnippetFailedWhenContentIsMissingAndContinuesWithOthers() {
        val orphan = ownedSnippet("Orphan")
        val healthy = ownedSnippet("Healthy")
        val orphanStatus = SnippetStatus(snippetId = orphan.id)
        snippetStore.put(healthy.id, "println(1);")
        whenever(snippetRepository.findAllById(any<Iterable<UUID>>())).thenReturn(listOf(orphan, healthy))
        whenever(statusRepository.findById(orphan.id)).thenReturn(Optional.of(orphanStatus))
        whenever(statusRepository.findById(healthy.id)).thenReturn(Optional.of(SnippetStatus(snippetId = healthy.id)))

        consumer.onMessage(ruleChangeEvent(3))

        verify(streamOps).add(
            argThat<ObjectRecord<String, LintRequestEvent>> {
                value.snippetId == healthy.id.toString()
            },
        )
        assertEquals(ComplianceStatus.FAILED, orphanStatus.status)
    }

    @Test
    fun toleratesFailureInOneSnippetAndContinuesProcessingOthers() {
        val broken = ownedSnippet("Broken")
        val healthy = ownedSnippet("Healthy")
        snippetStore.put(broken.id, "code 1")
        snippetStore.put(healthy.id, "code 2")
        whenever(snippetRepository.findAllById(any<Iterable<UUID>>())).thenReturn(listOf(broken, healthy))
        whenever(statusRepository.findById(broken.id)).thenThrow(RuntimeException("Status DB failure"))
        whenever(statusRepository.findById(healthy.id)).thenReturn(Optional.of(SnippetStatus(snippetId = healthy.id)))

        consumer.onMessage(ruleChangeEvent(3))

        verify(streamOps).add(
            argThat<ObjectRecord<String, LintRequestEvent>> {
                value.snippetId == healthy.id.toString()
            },
        )
    }

    @Test
    fun ignoresRecordWithoutUserId() {
        val record =
            ObjectRecord.create(
                "lint-rules",
                LintRulesChangeEvent(userId = "", rulesVersion = 1),
            )

        consumer.onMessage(record)

        verify(streamOps, never()).add(any())
    }

    @Test
    fun configuresStreamReceiverOptionsCorrectly() {
        val options = consumer.options()

        assertEquals(java.time.Duration.ofSeconds(10), options.pollTimeout)
        assertEquals(LintRulesChangeEvent::class.java, options.targetType)
    }

    @Test
    fun retriesFetchingContentWhenStorageFailsInitially() {
        val snippet = ownedSnippet("Retry Snippet")
        val status = SnippetStatus(snippetId = snippet.id)
        val mockStore = mock<ingsis.snippet.service.store.SnippetStore>()
        whenever(mockStore.get(snippet.id))
            .thenThrow(RuntimeException("Glitch"))
            .thenReturn("println(42);")

        val testConsumer =
            RuleChangeConsumer(
                snippetRepository = snippetRepository,
                statusRepository = statusRepository,
                snippetStore = mockStore,
                permissionClient = fakePermissionClient,
                lintRequestProducer = producer,
                redisTemplate = redisTemplate,
                work = StreamWorkQueue.direct("lint-rules"),
            )
        whenever(snippetRepository.findAllById(argThat<Iterable<UUID>> { toList() == listOf(snippet.id) }))
            .thenReturn(listOf(snippet))
        whenever(statusRepository.findById(snippet.id)).thenReturn(Optional.of(status))

        testConsumer.onMessage(ruleChangeEvent(1))

        verify(streamOps).add(
            argThat<ObjectRecord<String, LintRequestEvent>> {
                value.content == "println(42);"
            },
        )
    }
}
