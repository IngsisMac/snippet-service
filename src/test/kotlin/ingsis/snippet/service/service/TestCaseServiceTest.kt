package ingsis.snippet.service.service

import com.fasterxml.jackson.databind.ObjectMapper
import ingsis.snippet.service.client.dto.PermissionLevel
import ingsis.snippet.service.client.fake.FakePermissionClient
import ingsis.snippet.service.client.fake.FakeRunnerClient
import ingsis.snippet.service.controller.dto.CreateTestCaseRequest
import ingsis.snippet.service.domain.model.Snippet
import ingsis.snippet.service.domain.model.TestCase
import ingsis.snippet.service.domain.repository.SnippetRepository
import ingsis.snippet.service.domain.repository.TestCaseRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class TestCaseServiceTest {
    private lateinit var testCaseRepository: TestCaseRepository
    private lateinit var snippetRepository: SnippetRepository
    private lateinit var permissionClient: FakePermissionClient
    private lateinit var runnerClient: FakeRunnerClient
    private lateinit var objectMapper: ObjectMapper
    private lateinit var testCaseService: TestCaseService

    private val ownerId = "auth0|owner"
    private val readerId = "auth0|reader"
    private val strangerId = "auth0|stranger"
    private val snippetId = UUID.randomUUID()
    private val sampleSnippet =
        Snippet(
            id = snippetId,
            name = "Test Snippet",
            ownerId = ownerId,
            language = "printscript",
            version = "1.1",
            content = "println(10);",
        )

    @BeforeEach
    fun setUp() {
        testCaseRepository = mock()
        snippetRepository = mock()
        permissionClient = FakePermissionClient()
        runnerClient = FakeRunnerClient()
        objectMapper = ObjectMapper()
        testCaseService =
            TestCaseService(
                testCaseRepository = testCaseRepository,
                snippetRepository = snippetRepository,
                permissionClient = permissionClient,
                runnerClient = runnerClient,
                objectMapper = objectMapper,
            )

        permissionClient.setPermission(snippetId, ownerId, PermissionLevel.OWNER)
        permissionClient.setPermission(snippetId, readerId, PermissionLevel.READ)
    }

    @Test
    fun shouldCreateTestCaseSuccessfullyWhenUserIsOwner() {
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

        val response = testCaseService.createTestCase(snippetId, ownerId, request)

        assertNotNull(response)
        assertEquals("Passing case", response.name)
        assertEquals(listOf("5", "10"), response.inputs)
        assertEquals(listOf("15"), response.expectedOutputs)
        assertEquals(mapOf("MODE" to "TEST"), response.env)
        verify(testCaseRepository).save(any<TestCase>())
    }

    @Test
    fun shouldThrowForbiddenWhenCreatingTestCaseWithoutWritePermission() {
        val request = CreateTestCaseRequest(name = "Case")

        assertThrows<ResponseStatusException> {
            testCaseService.createTestCase(snippetId, readerId, request)
        }
    }

    @Test
    fun shouldThrowNotFoundWhenCreatingTestCaseForNonExistentSnippet() {
        val nonExistentSnippetId = UUID.randomUUID()
        permissionClient.setPermission(nonExistentSnippetId, ownerId, PermissionLevel.OWNER)
        whenever(snippetRepository.existsById(nonExistentSnippetId)).thenReturn(false)

        assertThrows<ResponseStatusException> {
            testCaseService.createTestCase(nonExistentSnippetId, ownerId, CreateTestCaseRequest(name = "Case"))
        }
    }

    @Test
    fun shouldListTestCasesWhenUserHasReadPermission() {
        val testCase =
            TestCase(
                snippetId = snippetId,
                name = "Case 1",
                inputs = "[\"1\"]",
                expectedOutputs = "[\"1\"]",
            )

        whenever(snippetRepository.existsById(snippetId)).thenReturn(true)
        whenever(testCaseRepository.findAllBySnippetId(snippetId)).thenReturn(listOf(testCase))

        val list = testCaseService.listTestCases(snippetId, readerId)

        assertEquals(1, list.size)
        assertEquals("Case 1", list[0].name)
    }

    @Test
    fun shouldThrowForbiddenWhenListingTestCasesWithoutPermission() {
        assertThrows<ResponseStatusException> {
            testCaseService.listTestCases(snippetId, strangerId)
        }
    }

    @Test
    fun shouldDeleteTestCaseSuccessfullyWhenUserIsOwner() {
        val testCaseId = UUID.randomUUID()
        whenever(snippetRepository.existsById(snippetId)).thenReturn(true)

        testCaseService.deleteTestCase(snippetId, testCaseId, ownerId)

        verify(testCaseRepository).deleteBySnippetIdAndId(snippetId, testCaseId)
    }

    @Test
    fun shouldRunSingleTestCaseSuccessfully() {
        val testCaseId = UUID.randomUUID()
        val testCase =
            TestCase(
                id = testCaseId,
                snippetId = snippetId,
                name = "Case 1",
                inputs = "[]",
                expectedOutputs = "[\"10\"]",
            )

        whenever(snippetRepository.findById(snippetId)).thenReturn(Optional.of(sampleSnippet))
        whenever(testCaseRepository.findBySnippetIdAndId(snippetId, testCaseId)).thenReturn(Optional.of(testCase))
        runnerClient.defaultTestPassed = true

        val result = testCaseService.runTestCase(snippetId, testCaseId, readerId)

        assertTrue(result.passed)
        assertEquals(testCaseId, result.testCaseId)
        assertEquals("Case 1", result.name)
        assertEquals(listOf("10"), result.expectedOutputs)
    }

    @Test
    fun shouldReturnFailedTestResultWhenRunnerExecutionDoesNotPass() {
        val testCaseId = UUID.randomUUID()
        val testCase =
            TestCase(
                id = testCaseId,
                snippetId = snippetId,
                name = "Failing Case",
                inputs = "[]",
                expectedOutputs = "[\"10\"]",
            )

        whenever(snippetRepository.findById(snippetId)).thenReturn(Optional.of(sampleSnippet))
        whenever(testCaseRepository.findBySnippetIdAndId(snippetId, testCaseId)).thenReturn(Optional.of(testCase))
        runnerClient.defaultTestPassed = false
        runnerClient.defaultActualOutputs = listOf("20")
        runnerClient.defaultTestErrors = listOf("Output mismatch")

        val result = testCaseService.runTestCase(snippetId, testCaseId, readerId)

        assertFalse(result.passed)
        assertEquals(listOf("20"), result.actualOutputs)
        assertEquals(1, result.errors.size)
    }

    @Test
    fun shouldRunAllTestCasesSuccessfully() {
        val testCase1 =
            TestCase(
                id = UUID.randomUUID(),
                snippetId = snippetId,
                name = "C1",
                inputs = "[]",
                expectedOutputs = "[\"10\"]"
            )
        val testCase2 =
            TestCase(
                id = UUID.randomUUID(),
                snippetId = snippetId,
                name = "C2",
                inputs = "[]",
                expectedOutputs = "[\"10\"]"
            )

        whenever(snippetRepository.findById(snippetId)).thenReturn(Optional.of(sampleSnippet))
        whenever(testCaseRepository.findAllBySnippetId(snippetId)).thenReturn(listOf(testCase1, testCase2))
        runnerClient.defaultTestPassed = true

        val results = testCaseService.runAllTestCases(snippetId, readerId)

        assertEquals(2, results.size)
        assertTrue(results[0].passed)
        assertTrue(results[1].passed)
    }
}
