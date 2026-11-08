package ingsis.snippet.service.redis

import ingsis.snippet.redis.event.FormatRequestEvent
import ingsis.snippet.redis.event.FormatRulesChangeEvent
import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.client.fake.FakePermissionClient
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.store.InMemorySnippetStore
import ingsis.snippet.service.store.SnippetStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.connection.stream.ObjectRecord
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StreamOperations
import java.time.Duration
import java.util.UUID

class FormatRuleChangeConsumerTest {
    private lateinit var snippetRepository: SnippetRepository
    private lateinit var snippetStore: InMemorySnippetStore
    private lateinit var fakePermissionClient: FakePermissionClient
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var producerRedisTemplate: RedisTemplate<String, String>
    private lateinit var streamOps: StreamOperations<String, String, Any>
    private lateinit var producer: FormatRequestProducer
    private lateinit var consumer: FormatRuleChangeConsumer
    private val userId = "auth0|user1"

    @BeforeEach
    fun setUp() {
        snippetRepository = mock()
        snippetStore = InMemorySnippetStore()
        fakePermissionClient = FakePermissionClient()
        redisTemplate = mock()
        producerRedisTemplate = mock()
        streamOps = mock()

        whenever(producerRedisTemplate.opsForStream<String, Any>()).thenReturn(streamOps)
        whenever(streamOps.add(any())).thenReturn(RecordId.of("1-0"))

        producer = FormatRequestProducer(producerRedisTemplate)
        consumer =
            FormatRuleChangeConsumer(
                snippetRepository = snippetRepository,
                snippetStore = snippetStore,
                permissionClient = fakePermissionClient,
                formatRequestProducer = producer,
                redisTemplate = redisTemplate,
                work = StreamWorkQueue.direct("format-rules"),
            )
    }

    private fun ownedSnippet(name: String): Snippet {
        val snippet = Snippet(name = name, ownerId = userId, language = "printscript", version = "1.1")
        fakePermissionClient.setPermission(snippet.id, userId, PermissionLevel.OWNER)
        return snippet
    }

    private fun formatRuleChangeEvent(
        rulesVersion: Int,
        rules: String = "{}",
    ): ObjectRecord<String, FormatRulesChangeEvent> =
        ObjectRecord.create(
            "format-rules",
            FormatRulesChangeEvent(userId = userId, rulesVersion = rulesVersion, rules = rules),
        )

    @Test
    fun publishesOneFormatRequestPerOwnedSnippetWithContentFromStore() {
        val snippet = ownedSnippet("My Snippet")
        snippetStore.put(snippet.id, "let x = 1;")
        whenever(snippetRepository.findAllById(argThat<Iterable<UUID>> { toList() == listOf(snippet.id) }))
            .thenReturn(listOf(snippet))

        consumer.onMessage(formatRuleChangeEvent(2, "{\"indentation\":4}"))

        verify(streamOps).add(
            argThat<ObjectRecord<String, FormatRequestEvent>> {
                value.snippetId == snippet.id.toString() &&
                    value.content == "let x = 1;" &&
                    value.rulesVersion == 2 &&
                    value.rules == "{\"indentation\":4}"
            },
        )
    }

    @Test
    fun ignoresSnippetsTheUserOnlyHasSharedAccessTo() {
        val shared = Snippet(name = "Shared", ownerId = "auth0|other", language = "printscript", version = "1.1")
        fakePermissionClient.setPermission(shared.id, userId, PermissionLevel.WRITE)
        whenever(snippetRepository.findAllById(argThat<Iterable<UUID>> { toList().isEmpty() })).thenReturn(emptyList())

        consumer.onMessage(formatRuleChangeEvent(1))

        verify(streamOps, never()).add(any())
    }

    @Test
    fun skipsSnippetWhenContentIsMissingAndContinuesWithOthers() {
        val orphan = ownedSnippet("Orphan")
        val healthy = ownedSnippet("Healthy")
        snippetStore.put(healthy.id, "println(1);")
        whenever(snippetRepository.findAllById(any<Iterable<UUID>>())).thenReturn(listOf(orphan, healthy))

        consumer.onMessage(formatRuleChangeEvent(3))

        verify(streamOps).add(
            argThat<ObjectRecord<String, FormatRequestEvent>> {
                value.snippetId == healthy.id.toString()
            },
        )
    }

    @Test
    fun ignoresRecordWithoutUserId() {
        val record =
            ObjectRecord.create(
                "format-rules",
                FormatRulesChangeEvent(userId = "", rulesVersion = 1),
            )

        consumer.onMessage(record)

        verify(streamOps, never()).add(any())
    }

    @Test
    fun configuresStreamReceiverOptionsCorrectly() {
        val options = consumer.options()

        assertEquals(Duration.ofSeconds(10), options.pollTimeout)
        assertEquals(FormatRulesChangeEvent::class.java, options.targetType)
    }

    @Test
    fun retriesFetchingContentWhenStorageFailsInitially() {
        val snippet = ownedSnippet("Retry Snippet")
        val mockStore = mock<SnippetStore>()
        whenever(mockStore.get(snippet.id))
            .thenThrow(RuntimeException("Network glitch"))
            .thenReturn("let recovered = true;")

        val retryConsumer =
            FormatRuleChangeConsumer(
                snippetRepository = snippetRepository,
                snippetStore = mockStore,
                permissionClient = fakePermissionClient,
                formatRequestProducer = producer,
                redisTemplate = redisTemplate,
                work = StreamWorkQueue.direct("format-rules"),
            )
        whenever(snippetRepository.findAllById(argThat<Iterable<UUID>> { toList() == listOf(snippet.id) }))
            .thenReturn(listOf(snippet))

        retryConsumer.onMessage(formatRuleChangeEvent(1))

        verify(streamOps).add(
            argThat<ObjectRecord<String, FormatRequestEvent>> {
                value.content == "let recovered = true;"
            },
        )
    }

    @Test
    fun skipsSnippetWhenAllContentFetchRetriesFail() {
        val snippet = ownedSnippet("Failing Snippet")
        val mockStore = mock<SnippetStore>()
        whenever(mockStore.get(snippet.id)).thenThrow(RuntimeException("Permanent failure"))

        val retryConsumer =
            FormatRuleChangeConsumer(
                snippetRepository = snippetRepository,
                snippetStore = mockStore,
                permissionClient = fakePermissionClient,
                formatRequestProducer = producer,
                redisTemplate = redisTemplate,
                work = StreamWorkQueue.direct("format-rules"),
            )
        whenever(snippetRepository.findAllById(argThat<Iterable<UUID>> { toList() == listOf(snippet.id) }))
            .thenReturn(listOf(snippet))

        retryConsumer.onMessage(formatRuleChangeEvent(1))

        verify(streamOps, never()).add(any())
    }

    @Test
    fun toleratesFailureDuringProcessingAndContinues() {
        val broken = ownedSnippet("Broken")
        val healthy = ownedSnippet("Healthy")
        val mockStore = mock<SnippetStore>()
        whenever(mockStore.get(broken.id)).thenReturn("broken code")
        whenever(mockStore.get(healthy.id)).thenReturn("healthy code")
        whenever(snippetRepository.findAllById(any<Iterable<UUID>>())).thenReturn(listOf(broken, healthy))

        var callCount = 0
        whenever(streamOps.add(any())).thenAnswer {
            callCount++
            if (callCount == 1) {
                error("Redis emission failed")
            }
            RecordId.of("2-0")
        }

        val testConsumer =
            FormatRuleChangeConsumer(
                snippetRepository = snippetRepository,
                snippetStore = mockStore,
                permissionClient = fakePermissionClient,
                formatRequestProducer = producer,
                redisTemplate = redisTemplate,
                work = StreamWorkQueue.direct("format-rules"),
            )

        testConsumer.onMessage(formatRuleChangeEvent(1))

        verify(streamOps, times(2)).add(any<ObjectRecord<String, FormatRequestEvent>>())
    }
}
