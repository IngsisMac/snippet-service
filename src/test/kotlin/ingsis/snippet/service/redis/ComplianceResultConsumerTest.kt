package ingsis.snippet.service.redis

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
import org.springframework.data.redis.connection.stream.MapRecord
import java.util.Optional
import java.util.UUID

class ComplianceResultConsumerTest {
    private lateinit var statusRepository: SnippetStatusRepository
    private lateinit var consumer: ComplianceResultConsumer

    @BeforeEach
    fun setUp() {
        statusRepository = mock()
        consumer = ComplianceResultConsumer(statusRepository)
    }

    @Test
    fun shouldUpdateStatusWhenRulesVersionIsGreaterOrEqual() {
        val snippetId = UUID.randomUUID()
        val current = SnippetStatus(snippetId = snippetId, rulesVersion = 2, status = ComplianceStatus.PENDING)
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(current))
        whenever(statusRepository.save(any<SnippetStatus>())).thenAnswer { it.arguments[0] }

        val payload =
            mapOf(
                "snippetId" to snippetId.toString(),
                "rulesVersion" to "3",
                "status" to "COMPLIANT",
                "findingsCount" to "0",
            )
        val record = MapRecord.create("compliance-results-stream", payload)

        consumer.processResult(record)

        assertEquals(3, current.rulesVersion)
        assertEquals(ComplianceStatus.COMPLIANT, current.status)
        verify(statusRepository).save(current)
    }

    @Test
    fun shouldDiscardObsoleteResultWhenRulesVersionIsLower() {
        val snippetId = UUID.randomUUID()
        val current = SnippetStatus(snippetId = snippetId, rulesVersion = 5, status = ComplianceStatus.COMPLIANT)
        whenever(statusRepository.findById(snippetId)).thenReturn(Optional.of(current))

        val payload =
            mapOf(
                "snippetId" to snippetId.toString(),
                "rulesVersion" to "4",
                "status" to "NOT_COMPLIANT",
                "findingsCount" to "2",
            )
        val record = MapRecord.create("compliance-results-stream", payload)

        consumer.processResult(record)

        assertEquals(5, current.rulesVersion)
        verify(statusRepository, never()).save(any())
    }

    @Test
    fun shouldIgnoreRecordWithInvalidSnippetId() {
        val payload = mapOf("snippetId" to "invalid-uuid")
        val record = MapRecord.create("compliance-results-stream", payload)

        consumer.processResult(record)

        verify(statusRepository, never()).save(any())
    }
}
