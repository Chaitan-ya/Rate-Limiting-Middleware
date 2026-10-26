package com.cp.rlm.algorithms;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class TokenBucketRateLimiter {
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> script;
    public TokenBucketRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        String luaScript =
                "local key = KEYS[1] \n" +
                "local capacity = tonumber(ARGV[1]) \n" +
                "local refill_rate = tonumber(ARGV[2]) \n" +
                "local current_time = tonumber(ARGV[3]) \n" +
                "local window = tonumber(ARGV[4]) \n" +
                " \n" +
                "local data = redis.call('HMGET', key, 'tokens', 'last_refill_time') \n" +
                "local tokens = tonumber(data[1]) \n" +
                "local last_refill_time = tonumber(data[2]) \n" +
                " \n" +
                "if not tokens then \n" +
                "    tokens = capacity \n" +
                "    last_refill_time = current_time \n" +
                "else \n" +
                "    local elapsed_time = (current_time - last_refill_time) / 1000.0 \n" +
                "    if elapsed_time > 0 then \n" +
                "        local tokens_to_add = elapsed_time * refill_rate \n" +
                "        tokens = math.min(capacity, tokens + tokens_to_add) \n" +
                "        last_refill_time = current_time \n" +
                "    end \n" +
                "end \n" +
                " \n" +
                "local ttl = math.max(60, math.ceil(window) + 60) \n" +
                "if tokens >= 1 then \n" +
                "    tokens = tokens - 1 \n" +
                "    redis.call('HSET', key, 'tokens', tostring(tokens), 'last_refill_time', tostring(last_refill_time)) \n" +
                "    redis.call('EXPIRE', key, ttl) \n" +
                "    return 1 \n" +
                "else \n" +
                "    redis.call('HSET', key, 'tokens', tostring(tokens), 'last_refill_time', tostring(last_refill_time)) \n" +
                "    redis.call('EXPIRE', key, ttl) \n" +
                "    return 0 \n" +
                "end \n";

        this.script = new DefaultRedisScript<>(luaScript, Long.class);
    }

    public boolean isAllowed(String clientId, long capacity, long windowInSeconds) {
        String redisKey = "rate_limit::token_bucket::" + clientId;
        double refillRate = (double) capacity / windowInSeconds;
        String currentTimeStr = String.valueOf(System.currentTimeMillis());

        try {
            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(redisKey),
                    String.valueOf(capacity),
                    String.valueOf(refillRate),
                    currentTimeStr,
                    String.valueOf(windowInSeconds)
            );
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("Redis error in TokenBucketRateLimiter for client [{}]: {}. Failing open.", clientId, e.getMessage());
            return true;
        }
    }
}
