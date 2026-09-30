package ingsis.snippet.service.client.dto

data class ValidateSnippetRequest(
    val content: String,
    val version: String,
)

data class ValidateSnippetResponse(
    val valid: Boolean,
    val errors: List<String> = emptyList(),
)
