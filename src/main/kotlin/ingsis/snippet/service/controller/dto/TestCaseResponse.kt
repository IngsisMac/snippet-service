package ingsis.snippet.service.controller.dto

import ingsis.snippet.service.domain.model.TestCaseResult
import ingsis.snippet.service.domain.model.TestRunStatus
import java.time.Instant
import java.util.UUID

data class TestCaseResponse(
    val id: UUID,
    val snippetId: UUID,
    val name: String,
    val inputs: List<String>,
    val expectedOutputs: List<String>,
    val env: Map<String, String>?,
    /** Último resultado conocido; nulo si el test case nunca se corrió. */
    val lastRun: TestRunResponse? = null,
)

/**
 * Resultado persistido de un test case. `PENDING` significa que el contenido del snippet cambió y
 * la corrida automática todavía no terminó, así que `executedAt` viene nulo.
 */
data class TestRunResponse(
    val status: TestRunStatus,
    val actualOutputs: List<String>,
    val errors: List<String>,
    val executedAt: Instant?,
) {
    companion object {
        fun from(
            result: TestCaseResult,
            parse: (String) -> List<String>,
        ): TestRunResponse =
            TestRunResponse(
                status = result.status,
                actualOutputs = parse(result.actualOutputs),
                errors = parse(result.errors),
                executedAt = result.executedAt,
            )
    }
}

data class TestCaseExecutionResponse(
    val testCaseId: UUID,
    val name: String,
    val passed: Boolean,
    val actualOutputs: List<String>,
    val expectedOutputs: List<String>,
    val errors: List<String>,
)
