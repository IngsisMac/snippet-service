package ingsis.snippet.service.domain.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

enum class TestRunStatus {
    /** Encolado: el contenido cambió y la corrida automática todavía no terminó. */
    PENDING,
    PASSED,
    FAILED,
}

/**
 * Último resultado conocido de un test case. Se guarda solo el último y no un historial: lo que
 * la consigna pide es que el usuario vea cuáles pasan hoy, y un historial obligaría a decidir una
 * política de retención sin que nadie la consuma.
 *
 * `executedAt` es nulo mientras el estado es [TestRunStatus.PENDING].
 */
@Entity
@Table(name = "test_case_results")
class TestCaseResult(
    @Id
    @Column(name = "test_case_id")
    val testCaseId: UUID,
    @Column(name = "snippet_id", nullable = false)
    val snippetId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TestRunStatus = TestRunStatus.PENDING,
    @Column(name = "actual_outputs", nullable = false, columnDefinition = "TEXT")
    var actualOutputs: String = "[]",
    @Column(nullable = false, columnDefinition = "TEXT")
    var errors: String = "[]",
    @Column(name = "executed_at")
    var executedAt: Instant? = null,
)
