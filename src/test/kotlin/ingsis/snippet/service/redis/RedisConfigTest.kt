package ingsis.snippet.service.redis

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.data.redis.connection.RedisConnectionFactory

class RedisConfigTest {
    private lateinit var connectionFactory: RedisConnectionFactory
    private lateinit var redisConfig: RedisConfig

    @BeforeEach
    fun setUp() {
        connectionFactory = mock()
        redisConfig = RedisConfig()
    }

    @Test
    fun createsRedisTemplateInstance() {
        val template = redisConfig.redisTemplate(connectionFactory)

        assertNotNull(template)
    }
}
