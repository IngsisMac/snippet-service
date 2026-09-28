package ingsis.snippet.service.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.controller.dto.CreateTestCaseRequest
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
    private val objectMapper: ObjectMapper,
) {
    @Transactional
    fun createTestCase(
        snippetId: UUID,
        request: CreateTestCaseRequest,
    ): TestCaseResponse {
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
    fun listTestCases(snippetId: UUID): List<TestCaseResponse> {
        if (!snippetRepository.existsById(snippetId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found")
        }
        return testCaseRepository.findAllBySnippetId(snippetId).map { mapToResponse(it) }
    }

    @Transactional
    fun deleteTestCase(
        snippetId: UUID,
        testCaseId: UUID,
    ) {
        if (!snippetRepository.existsById(snippetId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Snippet not found")
        }
        testCaseRepository.deleteBySnippetIdAndId(snippetId, testCaseId)
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
