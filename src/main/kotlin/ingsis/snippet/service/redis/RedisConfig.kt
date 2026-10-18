package ingsis.snippet.service.redis

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class RedisConfig {
    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> =
        StringRedisTemplate(connectionFactory)

    /**
     * Los consumidores procesan fuera del hilo que entrega el mensaje (ver [StreamWorkQueue]), así
     * que delimitan la transacción a mano: `@Transactional` sobre `onMessage` no aplicaría.
     */
    @Bean
    fun streamTransactionTemplate(transactionManager: PlatformTransactionManager): TransactionTemplate =
        TransactionTemplate(transactionManager)
}
