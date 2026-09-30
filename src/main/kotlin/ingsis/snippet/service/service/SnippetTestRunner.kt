package ingsis.snippet.service.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.client.RunnerClient
import ingsis.snippet.service.domain.model.TestCase
import ingsis.snippet.service.domain.repository.TestCaseRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SnippetTestRunner(
    private val testCaseRepository: TestCaseRepository,
    private val runnerClient: RunnerClient,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Suppress("TooGenericExceptionCaught")
    fun runTestsQuietly(
        snippetId: UUID,
        content: String,
        version: String,
    ) {
        try {
            val testCases = testCaseRepository.findAllBySnippetId(snippetId)
            for (testCase in testCases) {
                executeTestCase(snippetId, content, version, testCase)
            }
        } catch (ex: Exception) {
            logger.warn("Auto-test execution failed for snippet {}: {}", snippetId, ex.message)
        }
    }

    private fun executeTestCase(
        snippetId: UUID,
        content: String,
        version: String,
        testCase: TestCase,
    ) {
        val inputs = parseList(testCase.inputs)
        val expectedOutputs = parseList(testCase.expectedOutputs)
        val env = parseMap(testCase.env)
        val result = runnerClient.runTest(content, version, inputs, expectedOutputs, env)
        logger.info(
            "Auto-test '{}' for snippet {}: passed={}",
            testCase.name,
            snippetId,
            result.passed,
        )
    }

    private fun parseList(json: String): List<String> =
        try {
            objectMapper.readValue(json, object : TypeReference<List<String>>() {})
        } catch (_: Exception) {
            emptyList()
        }

    private fun parseMap(json: String?): Map<String, String>? =
        json?.let {
            try {
                objectMapper.readValue(it, object : TypeReference<Map<String, String>>() {})
            } catch (_: Exception) {
                null
            }
        }
}
