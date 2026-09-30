package ingsis.snippet.service.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.client.RunnerClient
import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.controller.dto.CreateTestCaseRequest
import ingsis.snippet.service.controller.dto.TestCaseExecutionResponse
import ingsis.snippet.service.controller.dto.TestCaseResponse
import ingsis.snippet.service.domain.model.TestCase
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.TestCaseRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class TestCaseService(
    private val testCaseRepository: TestCaseRepository,
    private val snippetRepository: SnippetRepository,
    private val permissionClient: PermissionClient,
    private val runnerClient: RunnerClient,
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun createTestCase(
        snippetId: UUID,
        userId: String,
        request: CreateTestCaseRequest,
    ): TestCaseResponse {
        assertCanWrite(snippetId, userId)
        if (!snippetRepository.existsById(snippetId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found")
        }

        val testCase =
            TestCase(
                snippetId = snippetId,
                name = request.name,
                inputs = objectMapper.writeValueAsString(request.inputs),
                expectedOutputs = objectMapper.writeValueAsString(request.expectedOutputs),
                env = request.env?.let { objectMapper.writeValueAsString(it) },
            )
        val saved = testCaseRepository.save(testCase)
        return mapToResponse(saved)
    }

    @Transactional(readOnly = true)
    fun listTestCases(
        snippetId: UUID,
        userId: String,
    ): List<TestCaseResponse> {
        assertCanRead(snippetId, userId)
        if (!snippetRepository.existsById(snippetId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found")
        }
        return testCaseRepository.findAllBySnippetId(snippetId).map { mapToResponse(it) }
    }

    @Transactional
    fun deleteTestCase(
        snippetId: UUID,
        testCaseId: UUID,
        userId: String,
    ) {
        assertCanWrite(snippetId, userId)
        if (!snippetRepository.existsById(snippetId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found")
        }
        testCaseRepository.deleteBySnippetIdAndId(snippetId, testCaseId)
    }

    @Transactional(readOnly = true)
    fun runTestCase(
        snippetId: UUID,
        testCaseId: UUID,
        userId: String,
    ): TestCaseExecutionResponse {
        assertCanRead(snippetId, userId)
        val snippet =
            snippetRepository
                .findById(snippetId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found") }
        val testCase =
            testCaseRepository
                .findBySnippetIdAndId(snippetId, testCaseId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Test case not found") }

        return executeSingleTest(snippet.content, snippet.version, testCase)
    }

    @Transactional(readOnly = true)
    fun runAllTestCases(
        snippetId: UUID,
        userId: String,
    ): List<TestCaseExecutionResponse> {
        assertCanRead(snippetId, userId)
        val snippet =
            snippetRepository
                .findById(snippetId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found") }
        val testCases = testCaseRepository.findAllBySnippetId(snippetId)
        return testCases.map { executeSingleTest(snippet.content, snippet.version, it) }
    }

    private fun executeSingleTest(
        content: String,
        version: String,
        testCase: TestCase,
    ): TestCaseExecutionResponse {
        val inputs = parseList(testCase.inputs)
        val expectedOutputs = parseList(testCase.expectedOutputs)
        val env = parseMap(testCase.env)
        val result = runnerClient.runTest(content, version, inputs, expectedOutputs, env)
        return TestCaseExecutionResponse(
            testCaseId = testCase.id,
            name = testCase.name,
            passed = result.passed,
            actualOutputs = result.actualOutputs,
            expectedOutputs = result.expectedOutputs,
            errors = result.errors,
        )
    }

    private fun assertCanWrite(
        snippetId: UUID,
        userId: String,
    ) {
        val level = permissionClient.getPermission(snippetId, userId)
        if (level != PermissionLevel.OWNER && level != PermissionLevel.WRITE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Write permission required")
        }
    }

    private fun assertCanRead(
        snippetId: UUID,
        userId: String,
    ) {
        val level = permissionClient.getPermission(snippetId, userId)
        if (level == PermissionLevel.NONE) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Read permission required")
        }
    }

    private fun mapToResponse(testCase: TestCase): TestCaseResponse =
        TestCaseResponse(
            id = testCase.id,
            snippetId = testCase.snippetId,
            name = testCase.name,
            inputs = parseList(testCase.inputs),
            expectedOutputs = parseList(testCase.expectedOutputs),
            env = parseMap(testCase.env),
        )

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
