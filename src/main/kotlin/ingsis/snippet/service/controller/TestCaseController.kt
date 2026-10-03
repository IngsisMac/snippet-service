package ingsis.snippet.service.controller

import ingsis.snippet.service.controller.dto.CreateTestCaseRequest
import ingsis.snippet.service.controller.dto.TestCaseExecutionResponse
import ingsis.snippet.service.controller.dto.TestCaseResponse
import ingsis.snippet.service.service.TestCaseService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
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
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<TestCaseResponse> {
        val userId = jwt.subject
        val created = testCaseService.createTestCase(snippetId, userId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(created)
    }

    @GetMapping
    fun listTestCases(
        @PathVariable snippetId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): List<TestCaseResponse> {
        val userId = jwt.subject
        return testCaseService.listTestCases(snippetId, userId)
    }

    @PutMapping("/{testCaseId}")
    fun updateTestCase(
        @PathVariable snippetId: UUID,
        @PathVariable testCaseId: UUID,
        @Valid @RequestBody request: CreateTestCaseRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): TestCaseResponse = testCaseService.updateTestCase(snippetId, testCaseId, jwt.subject, request)

    @DeleteMapping("/{testCaseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteTestCase(
        @PathVariable snippetId: UUID,
        @PathVariable testCaseId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        val userId = jwt.subject
        testCaseService.deleteTestCase(snippetId, testCaseId, userId)
    }

    @PostMapping("/{testCaseId}/run")
    fun runTestCase(
        @PathVariable snippetId: UUID,
        @PathVariable testCaseId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): TestCaseExecutionResponse {
        val userId = jwt.subject
        return testCaseService.runTestCase(snippetId, testCaseId, userId)
    }

    @PostMapping("/run-all")
    fun runAllTestCases(
        @PathVariable snippetId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): List<TestCaseExecutionResponse> {
        val userId = jwt.subject
        return testCaseService.runAllTestCases(snippetId, userId)
    }
}
