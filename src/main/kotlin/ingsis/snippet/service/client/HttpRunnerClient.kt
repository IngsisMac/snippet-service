package ingsis.snippet.service.client

import ingsis.snippet.service.client.dto.TestSnippetClientRequest
import ingsis.snippet.service.client.dto.TestSnippetClientResponse
import ingsis.snippet.service.client.dto.ValidateSnippetRequest
import ingsis.snippet.service.client.dto.ValidateSnippetResponse
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient

class HttpRunnerClient(
    private val restClient: RestClient,
) : RunnerClient {
    override fun validate(
        content: String,
        version: String,
    ): ValidateSnippetResponse {
        val request = ValidateSnippetRequest(content = content, version = version)
        return restClient
            .post()
            .uri("/runner/validate")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(ValidateSnippetResponse::class.java)
            ?: ValidateSnippetResponse(valid = false, errors = listOf("Empty response from runner"))
    }

    override fun runTest(
        content: String,
        version: String,
        inputs: List<String>,
        expectedOutputs: List<String>,
        env: Map<String, String>?,
    ): TestSnippetClientResponse {
        val request =
            TestSnippetClientRequest(
                content = content,
                version = version,
                inputs = inputs,
                expectedOutputs = expectedOutputs,
                env = env,
            )
        return restClient
            .post()
            .uri("/runner/test")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(TestSnippetClientResponse::class.java)
            ?: TestSnippetClientResponse(
                passed = false,
                errors = listOf("Empty response from runner"),
            )
    }
}
