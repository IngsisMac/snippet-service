package ingsis.snippet.service.service

import ingsis.snippet.service.client.RunnerClient
import ingsis.snippet.service.config.AsyncConfig
import ingsis.snippet.service.controller.dto.TestCaseExecutionResponse
import ingsis.snippet.service.domain.model.TestCase
import ingsis.snippet.service.domain.model.TestCaseResult
import ingsis.snippet.service.domain.model.TestRunStatus
import ingsis.snippet.service.domain.repository.TestCaseRepository
import ingsis.snippet.service.domain.repository.TestCaseResultRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Instant
import java.util.UUID

/**
 * Corre los test cases de un snippet cuando su contenido cambia (US #16) y persiste el resultado
 * de cada uno para que la interfaz pueda mostrar cuáles pasan sin volver a ejecutarlos.
 *
 * La corrida es asincrónica: guardar un snippet con diez test cases no puede costar diez
 * ejecuciones sincrónicas contra el runner. Arranca recién después de que commitea la transacción
 * del `PUT`, así que nunca guarda resultados de un contenido que terminó descartándose.
 */
@Service
class SnippetTestRunner(
    private val testCaseRepository: TestCaseRepository,
    private val resultRepository: TestCaseResultRepository,
    private val runnerClient: RunnerClient,
    private val codec: TestCaseCodec,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Deja los test cases del snippet en `PENDING`. Corre dentro de la transacción del `PUT`. */
    fun markPending(snippetId: UUID) {
        val results =
            testCaseRepository.findAllBySnippetId(snippetId).map { testCase ->
                resultRepository
                    .findById(testCase.id)
                    .orElse(TestCaseResult(testCaseId = testCase.id, snippetId = snippetId))
                    .apply {
                        status = TestRunStatus.PENDING
                        executedAt = null
                    }
            }
        resultRepository.saveAll(results)
    }

    /** Último resultado de cada test case del snippet, indexado por id de test case. */
    @Transactional(readOnly = true)
    fun resultsOf(snippetId: UUID): Map<UUID, TestCaseResult> =
        resultRepository.findAllBySnippetId(snippetId).associateBy { it.testCaseId }

    /**
     * `REQUIRES_NEW` no es opcional: la transacción del `PUT` ya commiteó cuando se dispara este
     * listener, así que Spring rechaza el contexto al arrancar si la propagación es la de por
     * defecto. Cada corrida abre su propia transacción en el hilo del pool.
     */
    @Async(AsyncConfig.TEST_EXECUTOR)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Suppress("TooGenericExceptionCaught")
    fun onSnippetContentChanged(event: SnippetContentChangedEvent) {
        val testCases = testCaseRepository.findAllBySnippetId(event.snippetId)
        if (testCases.isEmpty()) return
        for (testCase in testCases) {
            try {
                val result = execute(event.snippetId, event.content, event.version, testCase)
                resultRepository.save(result)
            } catch (ex: Exception) {
                logger.warn("Auto-test '{}' failed for snippet {}: {}", testCase.name, event.snippetId, ex.message)
                resultRepository.save(failureFor(event.snippetId, testCase, ex.message))
            }
        }
        logger.info("Ran {} test cases for snippet {} after a content change", testCases.size, event.snippetId)
    }

    /**
     * Corrida pedida a mano. Persiste el resultado igual que la automática, para que el estado
     * que ve el usuario sea uno solo venga de donde venga la ejecución.
     */
    @Transactional
    fun runNow(
        snippetId: UUID,
        content: String,
        version: String,
        testCase: TestCase,
    ): TestCaseExecutionResponse {
        val result = execute(snippetId, content, version, testCase)
        resultRepository.save(result)
        val payload = codec.payloadOf(testCase)
        return TestCaseExecutionResponse(
            testCaseId = testCase.id,
            name = testCase.name,
            passed = result.status == TestRunStatus.PASSED,
            actualOutputs = codec.decodeList(result.actualOutputs),
            expectedOutputs = payload.expectedOutputs,
            errors = codec.decodeList(result.errors),
        )
    }

    private fun execute(
        snippetId: UUID,
        content: String,
        version: String,
        testCase: TestCase,
    ): TestCaseResult {
        val payload = codec.payloadOf(testCase)
        val result =
            runnerClient.runTest(content, version, payload.inputs, payload.expectedOutputs, payload.env)
        return TestCaseResult(
            testCaseId = testCase.id,
            snippetId = snippetId,
            status = if (result.passed) TestRunStatus.PASSED else TestRunStatus.FAILED,
            actualOutputs = codec.encode(result.actualOutputs),
            errors = codec.encode(result.errors),
            executedAt = Instant.now(),
        )
    }

    private fun failureFor(
        snippetId: UUID,
        testCase: TestCase,
        message: String?,
    ): TestCaseResult =
        TestCaseResult(
            testCaseId = testCase.id,
            snippetId = snippetId,
            status = TestRunStatus.FAILED,
            errors = codec.encode(listOf(message ?: "Test execution failed")),
            executedAt = Instant.now(),
        )
}
