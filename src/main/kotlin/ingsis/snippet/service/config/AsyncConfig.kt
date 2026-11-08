package ingsis.snippet.service.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * Pool propio para la corrida automática de test cases (US #16): guardar un snippet no puede
 * quedarse esperando a que terminen sus tests contra el runner. Está acotado a propósito —cada
 * tarea ocupa una conexión HTTP contra `runner-service` por test case— y descarta con
 * `AbortPolicy` cuando la cola se llena, en lugar de ejecutar en el hilo que encola
 * (`CallerRunsPolicy`), que es justamente el hilo de la request que queríamos liberar.
 */
@Configuration
@EnableAsync
class AsyncConfig {
    @Bean(TEST_EXECUTOR)
    fun testExecutor(): Executor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = CORE_POOL_SIZE
            maxPoolSize = MAX_POOL_SIZE
            queueCapacity = QUEUE_CAPACITY
            setThreadNamePrefix("snippet-tests-")
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS)
            initialize()
        }

    companion object {
        const val TEST_EXECUTOR = "testExecutor"
        private const val CORE_POOL_SIZE = 2
        private const val MAX_POOL_SIZE = 4
        private const val QUEUE_CAPACITY = 100
        private const val AWAIT_TERMINATION_SECONDS = 20
    }
}
