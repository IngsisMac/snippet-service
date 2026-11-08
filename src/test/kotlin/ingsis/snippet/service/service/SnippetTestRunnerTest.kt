package ingsis.snippet.service.service

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.client.fake.FakeRunnerClient
import ingsis.snippet.service.domain.model.TestCase
import ingsis.snippet.service.domain.model.TestCaseResult
import ingsis.snippet.service.domain.model.TestRunStatus
import ingsis.snippet.service.domain.repository.TestCaseRepository
import ingsis.snippet.service.domain.repository.TestCaseResultRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

/**
 * US #16: al cambiar el contenido, los test cases quedan pendientes y después se corren en
 * background, dejando guardado el resultado de cada uno.
 */
class SnippetTestRunnerTest {
    private lateinit var testCaseRepository: TestCaseRepository
    private lateinit var resultRepository: TestCaseResultRepository
    private lateinit var fakeRunnerClient: FakeRunnerClient
    private lateinit var testRunner: SnippetTestRunner

    private val snippetId = UUID.randomUUID()
    private val codec = TestCaseCodec(ObjectMapper())

    @BeforeEach
    fun setUp() {
        testCaseRepository = mock()
        resultRepository = mock()
        fakeRunnerClient = FakeRunnerClient()
        testRunner = SnippetTestRunner(testCaseRepository, resultRepository, fakeRunnerClient, codec)
        whenever(resultRepository.save(any<TestCaseResult>())).thenAnswer { it.arguments[0] }
        whenever(resultRepository.saveAll(any<List<TestCaseResult>>())).thenAnswer { it.arguments[0] }
        whenever(resultRepository.findById(any())).thenReturn(Optional.empty())
    }

    @Test
    fun shouldStoreAPassedResultForEveryTestCaseThatMatchesItsExpectedOutput() {
        val testCase = testCaseNamed("Passing test", expected = """["hello world"]""")
        whenever(testCaseRepository.findAllBySnippetId(snippetId)).thenReturn(listOf(testCase))
        fakeRunnerClient.defaultTestPassed = true

        testRunner.onSnippetContentChanged(contentChanged("""println("hello world");"""))

        val stored = savedResults().single()
        assertEquals(TestRunStatus.PASSED, stored.status)
        assertEquals(listOf("hello world"), codec.decodeList(stored.actualOutputs))
        assertNotNull(stored.executedAt)
    }

    @Test
    fun shouldStoreAFailedResultWhenTheOutputDoesNotMatch() {
        val testCase = testCaseNamed("Failing test", expected = """["42"]""")
        whenever(testCaseRepository.findAllBySnippetId(snippetId)).thenReturn(listOf(testCase))
        fakeRunnerClient.defaultTestPassed = false
        fakeRunnerClient.defaultActualOutputs = listOf("41")

        testRunner.onSnippetContentChanged(contentChanged("""println(41);"""))

        val stored = savedResults().single()
        assertEquals(TestRunStatus.FAILED, stored.status)
        assertEquals(listOf("41"), codec.decodeList(stored.actualOutputs))
    }

    @Test
    fun shouldRecordAFailureInsteadOfPropagatingWhenTheRunnerIsUnreachable() {
        val failing = testCaseNamed("Unreachable runner")
        whenever(testCaseRepository.findAllBySnippetId(snippetId)).thenReturn(listOf(failing))
        val exploding: ingsis.snippet.service.client.RunnerClient = mock()
        whenever(exploding.runTest(any(), any(), any(), any(), anyOrNullMap()))
            .thenThrow(RuntimeException("connection refused"))
        testRunner = SnippetTestRunner(testCaseRepository, resultRepository, exploding, codec)

        testRunner.onSnippetContentChanged(contentChanged("println(1);"))

        val stored = savedResults().single()
        assertEquals(TestRunStatus.FAILED, stored.status)
        assertTrue(codec.decodeList(stored.errors).single().contains("connection refused"))
    }

    @Test
    fun shouldLeaveEveryTestCasePendingWhileTheBackgroundRunHasNotFinished() {
        val testCase = testCaseNamed("Queued")
        whenever(testCaseRepository.findAllBySnippetId(snippetId)).thenReturn(listOf(testCase))

        testRunner.markPending(snippetId)

        val captor = argumentCaptor<List<TestCaseResult>>()
        verify(resultRepository).saveAll(captor.capture())
        val pending = captor.firstValue.single()
        assertEquals(TestRunStatus.PENDING, pending.status)
        assertNull(pending.executedAt)
    }

    @Test
    fun shouldStoreNothingWhenTheSnippetHasNoTestCases() {
        whenever(testCaseRepository.findAllBySnippetId(snippetId)).thenReturn(emptyList())

        testRunner.onSnippetContentChanged(contentChanged("println(1);"))

        verify(resultRepository, org.mockito.kotlin.never()).save(any<TestCaseResult>())
    }

    private fun contentChanged(content: String) = SnippetContentChangedEvent(snippetId, content, "1.1", "auth0|owner")

    private fun testCaseNamed(
        name: String,
        expected: String = "[]",
    ): TestCase =
        TestCase(
            snippetId = snippetId,
            name = name,
            inputs = "[]",
            expectedOutputs = expected,
        )

    private fun savedResults(): List<TestCaseResult> {
        val captor = argumentCaptor<TestCaseResult>()
        verify(resultRepository, org.mockito.kotlin.atLeastOnce()).save(captor.capture())
        return captor.allValues
    }

    private fun anyOrNullMap(): Map<String, String>? = org.mockito.kotlin.anyOrNull()
}
