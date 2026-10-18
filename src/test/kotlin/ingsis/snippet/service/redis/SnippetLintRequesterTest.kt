package ingsis.snippet.service.redis

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.redis.event.LintRequestEvent
import ingsis.snippet.service.client.fake.FakePermissionClient
import ingsis.snippet.service.service.SnippetContentChangedEvent
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
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
import java.util.UUID

/**
 * US #15: crear o editar un snippet pide su linteo con las reglas vigentes del dueño, en lugar
 * de dejarlo pendiente hasta el próximo cambio de reglas.
 */
class SnippetLintRequesterTest {
    private lateinit var fakePermissionClient: FakePermissionClient
    private lateinit var redisTemplate: RedisTemplate<String, String>
    private lateinit var streamOps: StreamOperations<String, String, Any>
    private lateinit var requester: SnippetLintRequester

    private val ownerId = "auth0|owner"
    private val snippetId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        fakePermissionClient = FakePermissionClient()
        redisTemplate = mock()
        streamOps = mock()
        whenever(redisTemplate.opsForStream<String, Any>()).thenReturn(streamOps)
        whenever(streamOps.add(any())).thenReturn(RecordId.of("1-0"))
        requester = SnippetLintRequester(fakePermissionClient, LintRequestProducer(redisTemplate), ObjectMapper())
    }

    private fun contentChanged(content: String) = SnippetContentChangedEvent(snippetId, content, "1.1", ownerId)

    @Test
    fun publishesALintRequestWithTheOwnersCurrentRulesAndVersion() {
        fakePermissionClient.setLintRules(ownerId, mapOf("identifier_format" to "snake case"), rulesVersion = 3)

        requester.onSnippetContentChanged(contentChanged("let userName: string = \"a\";"))

        verify(streamOps).add(
            argThat<ObjectRecord<String, LintRequestEvent>> {
                stream == "lint-request" &&
                    value.snippetId == snippetId.toString() &&
                    value.content == "let userName: string = \"a\";" &&
                    value.version == "1.1" &&
                    value.rulesVersion == 3 &&
                    value.rules == "{\"identifier_format\":\"snake case\"}"
            },
        )
    }

    @Test
    fun publishesWithEmptyRulesAndVersionZeroWhenTheOwnerNeverConfiguredLinting() {
        requester.onSnippetContentChanged(contentChanged("println(1);"))

        verify(streamOps).add(
            argThat<ObjectRecord<String, LintRequestEvent>> {
                value.rulesVersion == 0 && value.rules == "{}"
            },
        )
    }

    @Test
    fun leavesTheSnippetPendingWithoutFailingWhenThePermissionServiceIsDown() {
        fakePermissionClient.lintRulesFailure = IllegalStateException("permission-service unavailable")

        assertDoesNotThrow { requester.onSnippetContentChanged(contentChanged("println(1);")) }

        verify(streamOps, never()).add(any())
    }

    @Test
    fun leavesTheSnippetPendingWithoutFailingWhenRedisRejectsTheMessage() {
        fakePermissionClient.setLintRules(ownerId, emptyMap(), rulesVersion = 1)
        whenever(streamOps.add(any())).thenThrow(IllegalStateException("redis down"))

        assertDoesNotThrow { requester.onSnippetContentChanged(contentChanged("println(1);")) }
    }
}
