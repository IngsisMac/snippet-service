package ingsis.snippet.service.client.fake

import ingsis.snippet.service.client.RunnerClient
import ingsis.snippet.service.client.dto.ValidateSnippetResponse

class FakeRunnerClient(
    var defaultValid: Boolean = true,
    var defaultErrors: List<String> = emptyList(),
) : RunnerClient {
    private val configuredResults = mutableMapOf<String, ValidateSnippetResponse>()

    fun setValidationResult(
        content: String,
        valid: Boolean,
        errors: List<String> = emptyList(),
    ) {
        configuredResults[content] = ValidateSnippetResponse(valid = valid, errors = errors)
    }

    override fun validate(
        content: String,
        version: String,
    ): ValidateSnippetResponse =
        configuredResults[content] ?: ValidateSnippetResponse(
            valid = defaultValid,
            errors = defaultErrors,
        )
}
