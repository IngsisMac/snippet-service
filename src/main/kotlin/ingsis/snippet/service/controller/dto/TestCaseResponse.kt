package ingsis.snippet.service.controller.dto

import java.util.UUID

data class TestCaseResponse(
    val id: UUID,
    val snippetId: UUID,
    val name: String,
    val inputs: List<String>,
    val expectedOutputs: List<String>,
    val env: Map<String, String>?,
)

data class TestCaseExecutionResponse(
    val testCaseId: UUID,
    val name: String,
    val passed: Boolean,
    val actualOutputs: List<String>,
    val expectedOutputs: List<String>,
    val errors: List<String>,
)
