package ingsis.snippet.service.service

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.client.fake.FakeRunnerClient
import ingsis.snippet.service.domain.model.TestCase
import ingsis.snippet.service.domain.repository.TestCaseRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class SnippetTestRunnerTest {
    private lateinit var testCaseRepository: TestCaseRepository
    private lateinit var fakeRunnerClient: FakeRunnerClient
    private lateinit var testRunner: SnippetTestRunner
    private val objectMapper = ObjectMapper()

    @BeforeEach
    fun setUp() {
        testCaseRepository = mock()
        fakeRunnerClient = FakeRunnerClient()
        testRunner = SnippetTestRunner(testCaseRepository, fakeRunnerClient, objectMapper)
    }

    @Test
    fun shouldExecuteAllTestCasesForSnippetQuietly() {
        val snippetId = UUID.randomUUID()
        val testCase =
            TestCase(
                snippetId = snippetId,
                name = "Passing test",
                inputs = "[\"hello\"]",
                expectedOutputs = "[\"hello world\"]",
            )
        whenever(testCaseRepository.findAllBySnippetId(snippetId)).thenReturn(listOf(testCase))
        fakeRunnerClient.defaultTestPassed = true

        testRunner.runTestsQuietly(snippetId, "println(\"hello world\");", "1.1")

        // No exceptions thrown
    }

    @Test
    fun shouldCatchAndIgnoreExceptionsDuringTestRun() {
        val snippetId = UUID.randomUUID()
        whenever(testCaseRepository.findAllBySnippetId(snippetId)).thenThrow(RuntimeException("DB failure"))

        testRunner.runTestsQuietly(snippetId, "content", "1.1")

        // Verified that exception was caught quietly
    }
}
