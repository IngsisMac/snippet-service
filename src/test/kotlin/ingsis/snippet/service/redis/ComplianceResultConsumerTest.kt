package ingsis.snippet.service.redis

import ingsis.snippet.redis.event.ComplianceResultEvent
import ingsis.snippet.service.domain.model.ComplianceStatus
import ingsis.snippet.service.domain.model.SnippetStatus
import ingsis.snippet.service.domain.repository.SnippetStatusRepository
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

class ComplianceResultConsumerTest {
    private lateinit var statusRepository: SnippetStatusRepository
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var consumer: ComplianceResultConsumer

    @BeforeEach
    fun setUp() {
        statusRepository = mock()
        redisTemplate = mock()
        consumer =
            ComplianceResultConsumer(
                statusRepository,
                redisTemplate,
                mock(),
                StreamWorkQueue.direct("lint-result"),
            )
    }

    @Test
    fun updatesStatusWhenRulesVersionIsGreaterOrEqual() {
        val snippetId = UUID.randomUUID()
        val current = SnippetStatus(snippetId = snippetId, rulesVersion = 2, status = ComplianceStatus.PENDING)
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(current))
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        val event =
            ComplianceResultEvent(
                snippetId = snippetId.toString(),
                rulesVersion = 3,
                status = "COMPLIANT",
                findingsCount = 0,
            )
        val record = ObjectRecord.create("lint-result", event)

        consumer.onMessage(record)

        assertEquals(3, current.rulesVersion)
        assertEquals(ComplianceStatus.COMPLIANT, current.status)
        verify(statusRepository).save(current)
    }

    @Test
    fun storesTheFindingsCarriedByTheResultSoTheDetailCanShowThem() {
        val snippetId = UUID.randomUUID()
        val current = SnippetStatus(snippetId = snippetId, rulesVersion = 1)
        val findings = """[{"message":"Identifier is not in camel case","line":1,"column":5}]"""
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(current))
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        val event =
            ComplianceResultEvent(
                snippetId = snippetId.toString(),
                rulesVersion = 2,
                status = "NOT_COMPLIANT",
                findingsCount = 1,
                findings = findings,
            )

        consumer.onMessage(ObjectRecord.create("lint-result", event))

        assertEquals(findings, current.findings)
        assertEquals(1, current.findingsCount)
    }

    @Test
    fun keepsAnEmptyListWhenTheResultCarriesNoFindings() {
        val snippetId = UUID.randomUUID()
        val current = SnippetStatus(snippetId = snippetId, findings = """[{"message":"old"}]""")
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(current))
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        val event =
            ComplianceResultEvent(
                snippetId = snippetId.toString(),
                rulesVersion = 1,
                status = "COMPLIANT",
                findings = "",
            )

        consumer.onMessage(ObjectRecord.create("lint-result", event))

        assertEquals("[]", current.findings)
    }

    @Test
    fun discardsObsoleteResultWhenRulesVersionIsLower() {
        val snippetId = UUID.randomUUID()
        val current = SnippetStatus(snippetId = snippetId, rulesVersion = 5, status = ComplianceStatus.COMPLIANT)
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(current))

        val event =
            ComplianceResultEvent(
                snippetId = snippetId.toString(),
                rulesVersion = 4,
                status = "NOT_COMPLIANT",
                findingsCount = 2,
            )
        val record = ObjectRecord.create("lint-result", event)

        consumer.onMessage(record)

        assertEquals(5, current.rulesVersion)
        verify(statusRepository, never()).save(any())
    }

    @Test
    fun ignoresRecordWithInvalidSnippetId() {
        val event = ComplianceResultEvent(snippetId = "invalid-uuid", rulesVersion = 1)
        val record = ObjectRecord.create("lint-result", event)

        consumer.onMessage(record)

        verify(statusRepository, never()).save(any())
    }

    @Test
    fun fallsBackToFailedStatusWhenStatusStringIsUnknown() {
        val snippetId = UUID.randomUUID()
        val current = SnippetStatus(snippetId = snippetId, rulesVersion = 1, status = ComplianceStatus.PENDING)
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(current))
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        val event =
            ComplianceResultEvent(
                snippetId = snippetId.toString(),
                rulesVersion = 2,
                status = "UNKNOWN_STATUS",
                findingsCount = 1,
            )
        val record = ObjectRecord.create("lint-result", event)

        consumer.onMessage(record)

        assertEquals(2, current.rulesVersion)
        assertEquals(ComplianceStatus.FAILED, current.status)
        verify(statusRepository).save(current)
    }

    @Test
    fun configuresStreamReceiverOptionsCorrectly() {
        val options = consumer.options()

        assertEquals(java.time.Duration.ofSeconds(10), options.pollTimeout)
        assertEquals(ComplianceResultEvent::class.java, options.targetType)
    }
}
