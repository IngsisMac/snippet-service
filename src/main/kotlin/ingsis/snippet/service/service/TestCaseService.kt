package ingsis.snippet.service.service

import ingsis.snippet.service.client.PermissionClient
import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.controller.dto.CreateTestCaseRequest
import ingsis.snippet.service.controller.dto.TestCaseExecutionResponse
import ingsis.snippet.service.controller.dto.TestCaseResponse
import ingsis.snippet.service.controller.dto.TestRunResponse
import ingsis.snippet.service.domain.model.TestCase
import ingsis.snippet.service.domain.model.TestCaseResult
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.TestCaseRepository
import ingsis.snippet.service.store.SnippetStore
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
    private val snippetStore: SnippetStore,
    private val codec: TestCaseCodec,
    private val testRunner: SnippetTestRunner,
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
                inputs = codec.encode(request.inputs),
                expectedOutputs = codec.encode(request.expectedOutputs),
                env = request.env?.let { codec.encodeEnv(it) },
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
        val resultsByTestCase = testRunner.resultsOf(snippetId)
        return testCaseRepository.findAllBySnippetId(snippetId).map { mapToResponse(it, resultsByTestCase[it.id]) }
    }

    @Transactional
    fun updateTestCase(
        snippetId: UUID,
        testCaseId: UUID,
        userId: String,
        request: CreateTestCaseRequest,
    ): TestCaseResponse {
        assertCanWrite(snippetId, userId)
        val testCase =
            testCaseRepository
                .findBySnippetIdAndId(snippetId, testCaseId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Test case not found") }
        testCase.name = request.name
        testCase.inputs = codec.encode(request.inputs)
        testCase.expectedOutputs = codec.encode(request.expectedOutputs)
        testCase.env = request.env?.let { codec.encodeEnv(it) }
        return mapToResponse(testCaseRepository.save(testCase))
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

    @Transactional
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

        return testRunner.runNow(snippetId, loadContent(snippetId), snippet.version, testCase)
    }

    @Transactional
    fun runAllTestCases(
        snippetId: UUID,
        userId: String,
    ): List<TestCaseExecutionResponse> {
        assertCanRead(snippetId, userId)
        val snippet =
            snippetRepository
                .findById(snippetId)
                .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found") }
        val content = loadContent(snippetId)
        val testCases = testCaseRepository.findAllBySnippetId(snippetId)
        return testCases.map { testRunner.runNow(snippetId, content, snippet.version, it) }
    }

    private fun loadContent(snippetId: UUID): String =
        snippetStore.get(snippetId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet content not found in storage")

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

    private fun mapToResponse(
        testCase: TestCase,
        result: TestCaseResult? = null,
    ): TestCaseResponse {
        val payload = codec.payloadOf(testCase)
        return TestCaseResponse(
            id = testCase.id,
            snippetId = testCase.snippetId,
            name = testCase.name,
            inputs = payload.inputs,
            expectedOutputs = payload.expectedOutputs,
            env = payload.env,
            lastRun = result?.let { TestRunResponse.from(it, codec::decodeList) },
        )
    }
}
