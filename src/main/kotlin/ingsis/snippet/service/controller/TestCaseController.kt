package ingsis.snippet.service.controller

import ingsis.snippet.service.controller.dto.CreateTestCaseRequest
import ingsis.snippet.service.controller.dto.TestCaseResponse
import ingsis.snippet.service.service.TestCaseService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/snippets/{snippetId}/test-cases")
class TestCaseController(
    private val testCaseService: TestCaseService,
) {
    @PostMapping
    fun createTestCase(
        @PathVariable snippetId: UUID,
        @Valid @RequestBody request: CreateTestCaseRequest,
    ): ResponseEntity<TestCaseResponse> {
        val created = testCaseService.createTestCase(snippetId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping
    fun listTestCases(
        @PathVariable snippetId: UUID,
    ): List<TestCaseResponse> = testCaseService.listTestCases(snippetId)

    @DeleteMapping("/{testCaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTestCase(
        @PathVariable snippetId: UUID,
        @PathVariable testCaseId: UUID,
    ) {
        testCaseService.deleteTestCase(snippetId, testCaseId)
    }
}
