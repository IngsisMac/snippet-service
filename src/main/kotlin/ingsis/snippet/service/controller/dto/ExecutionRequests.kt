package ingsis.snippet.service.controller.dto

import java.util.UUID

data class StartExecutionRequest(
    val env: Map<String, String> = emptyMap(),
)

data class StartExecutionResponse(
    val executionId: UUID,
)

data class ExecutionInputRequest(
    val value: String = "",
)
