package ingsis.snippet.service.redis

import org.austral.ingsis.redis.RedisStreamProducer
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component

@Component
class FormatRequestProducer(
    redisTemplate: RedisTemplate<String, String>,
) : RedisStreamProducer(streamKey = "format-request", redis = redisTemplate)
