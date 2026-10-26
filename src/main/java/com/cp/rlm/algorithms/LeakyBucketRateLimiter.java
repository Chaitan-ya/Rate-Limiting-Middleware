package com.cp.rlm.algorithms;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LeakyBucketRateLimiter {
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> script;

    public LeakyBucketRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        String luaScript =
                "local key = KEYS[1] \n" +
                "local capacity = tonumber(ARGV[1]) \n" +
                "local leak_rate = tonumber(ARGV[2]) \n" +
                "local now = tonumber(ARGV[3]) \n" +
                "local window = tonumber(ARGV[4]) \n" +
                " \n" +
                "local data = redis.call('HMGET', key, 'water', 'last_leak_time') \n" +
                "local water = tonumber(data[1]) \n" +
                "local last_leak_time = tonumber(data[2]) \n" +
                " \n" +
                "if not water then \n" +
                "    water = 0 \n" +
                "    last_leak_time = now \n" +
                "else \n" +
                "    local elapsed = (now - last_leak_time) / 1000.0 \n" +
                "    if elapsed > 0 then \n" +
                "        local leaked = elapsed * leak_rate \n" +
                "        water = math.max(0, water - leaked) \n" +
                "        last_leak_time = now \n" +
                "    end \n" +
                "end \n" +
                " \n" +
                "local ttl = math.max(60, math.ceil(window) + 60) \n" +
                "if (water + 1) <= capacity then \n" +
                "    water = water + 1 \n" +
                "    redis.call('HSET', key, 'water', tostring(water), 'last_leak_time', tostring(last_leak_time)) \n" +
                "    redis.call('EXPIRE', key, ttl) \n" +
                "    return 1 \n" +
                "else \n" +
                "    redis.call('HSET', key, 'water', tostring(water), 'last_leak_time', tostring(last_leak_time)) \n" +
                "    redis.call('EXPIRE', key, ttl) \n" +
                "    return 0 \n" +
                "end \n";

        this.script = new DefaultRedisScript<>(luaScript, Long.class);
    }

    public boolean isAllowed(String clientId, long limit, long windowInSeconds) {
        String redisKey = "rate_limit::leaky_bucket::" + clientId;
        double leakRate = (double) limit / windowInSeconds;
        String currentTimeMs = String.valueOf(System.currentTimeMillis());

        try {
            Long result = redisTemplate.execute(
                    script,
                    Collections.singletonList(redisKey),
                    String.valueOf(limit),
                    String.valueOf(leakRate),
                    currentTimeMs,
                    String.valueOf(windowInSeconds)
            );
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("Redis error in LeakyBucketRateLimiter for client [{}]: {}. Failing open.", clientId, e.getMessage());
            return true;
        }
    }
}

