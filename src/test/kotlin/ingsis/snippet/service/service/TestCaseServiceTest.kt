package ingsis.snippet.service.service

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.controller.dto.CreateTestCaseRequest
import ingsis.snippet.service.domain.model.TestCase
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.TestCaseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class TestCaseServiceTest {
    private lateinit var testCaseRepository: TestCaseRepository
    private lateinit var snippetRepository: SnippetRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var testCaseService: TestCaseService

    @BeforeEach
    fun setUp() {
        testCaseRepository = mock()
        snippetRepository = mock()
        objectMapper = ObjectMapper()
        testCaseService = TestCaseService(testCaseRepository, snippetRepository, objectMapper)
    }

    @Test
    fun shouldCreateTestCaseSuccessfully() {
        val snippetId = UUID.randomUUID()
        val request =
            CreateTestCaseRequest(
                name = "Passing case",
                inputs = listOf("5", "10"),
                expectedOutputs = listOf("15"),
                env = mapOf("MODE" to "TEST"),
            )

        val testCase =
            TestCase(
                snippetId = snippetId,
                name = request.name,
                inputs = objectMapper.writeValueAsString(request.inputs),
                expectedOutputs = objectMapper.writeValueAsString(request.expectedOutputs),
                env = objectMapper.writeValueAsString(request.env),
            )

        whenever(snippetRepository.existsById(snippetId)).thenReturn(true)
        whenever(testCaseRepository.save(any<TestCase>())).thenReturn(testCase)

        val response = testCaseService.createTestCase(snippetId, request)

        assertNotNull(response)
        assertEquals("Passing case", response.name)
        assertEquals(listOf("5", "10"), response.inputs)
        assertEquals(listOf("15"), response.expectedOutputs)
        assertEquals(mapOf("MODE" to "TEST"), response.env)
        verify(testCaseRepository).save(any<TestCase>())
    }

    @Test
    fun shouldThrowNotFoundWhenCreatingTestCaseForNonExistentSnippet() {
        val snippetId = UUID.randomUUID()
        val request = CreateTestCaseRequest(name = "Case")

        whenever(snippetRepository.existsById(snippetId)).thenReturn(false)

        assertThrows<ResponseStatusException> {
            testCaseService.createTestCase(snippetId, request)
        }
    }

    @Test
    fun shouldListTestCasesBySnippetId() {
        val snippetId = UUID.randomUUID()
        val testCase =
            TestCase(
                snippetId = snippetId,
                name = "Case 1",
                inputs = "[\"1\"]",
                expectedOutputs = "[\"1\"]",
            )

        whenever(snippetRepository.existsById(snippetId)).thenReturn(true)
        whenever(testCaseRepository.findAllBySnippetId(snippetId)).thenReturn(listOf(testCase))

        val list = testCaseService.listTestCases(snippetId)

        assertEquals(1, list.size)
        assertEquals("Case 1", list[0].name)
    }

    @Test
    fun shouldDeleteTestCase() {
        val snippetId = UUID.randomUUID()
        val testCaseId = UUID.randomUUID()

        whenever(snippetRepository.existsById(snippetId)).thenReturn(true)

        testCaseService.deleteTestCase(snippetId, testCaseId)

        verify(testCaseRepository).deleteBySnippetIdAndId(snippetId, testCaseId)
    }
}
