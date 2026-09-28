package ingsis.snippet.service.controller.dto

import jakarta.validation.constraints.NotBlank

data class CreateTestCaseRequest(
    @field:NotBlank
    val name: String,
    val inputs: List<String> = emptyList(),
    val expectedOutputs: List<String> = emptyList(),
    val env: Map<String, String>? = null,
)
