package ingsis.snippet.service.client.dto

data class ValidateSnippetRequest(
    val content: String,
    val version: String,
)

data class ValidateSnippetResponse(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
)

data class TestSnippetClientRequest(
    val content: String,
    val version: String,
    val inputs: List<String> = emptyList(),
    val expectedOutputs: List<String> = emptyList(),
    val env: Map<String, String>? = null,
)

data class TestSnippetClientResponse(
    val passed: Boolean,
    val actualOutputs: List<String> = emptyList(),
    val expectedOutputs: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

data class FormatSnippetClientRequest(
    val content: String,
    val version: String,
    val rules: Map<String, Any> = emptyMap(),
)

data class FormatSnippetClientResponse(
    val formattedContent: String,
)

data class LintFindingClientDto(
    val message: String,
    val line: Int,
    val column: Int,
)

data class LintSnippetClientRequest(
    val content: String,
    val version: String,
    val rules: Map<String, Any> = emptyMap(),
)

data class LintSnippetClientResponse(
    val findings: List<LintFindingClientDto> = emptyList(),
    val findingsCount: Int = 0,
)
