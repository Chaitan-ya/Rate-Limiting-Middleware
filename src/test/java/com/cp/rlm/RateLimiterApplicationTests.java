package com.cp.rlm;

import com.cp.rlm.algorithms.LeakyBucketRateLimiter;
import com.cp.rlm.algorithms.TokenBucketRateLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class RateLimiterApplicationTests {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @Autowired
    private TokenBucketRateLimiter tokenBucketRateLimiter;

    @Autowired
    private LeakyBucketRateLimiter leakyBucketRateLimiter;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void testTokenBucketAllowed() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(1L);

        mockMvc.perform(get("/api/token-bucket").header("X-API-Key", "test-client-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Success"))
                .andExpect(jsonPath("$.message").value("Accessed token bucket protected endpoint."));
    }

    @Test
    void testTokenBucketThrottled() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(0L);

        mockMvc.perform(get("/api/token-bucket").header("X-API-Key", "test-client-2"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.policy").value("TOKEN_BUCKET"));
    }

    @Test
    void testLeakyBucketAllowed() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(1L);

        mockMvc.perform(get("/api/leaky-bucket").header("X-API-Key", "test-client-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Success"))
                .andExpect(jsonPath("$.message").value("Accessed leaky bucket protected endpoint."));
    }

    @Test
    void testLeakyBucketThrottled() throws Exception {
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(0L);

        mockMvc.perform(get("/api/leaky-bucket").header("X-API-Key", "test-client-4"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.policy").value("LEAKY_BUCKET"));
    }

    @Test
    void testDirectAlgorithmEvaluation() {
        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(1L);
        assertTrue(tokenBucketRateLimiter.isAllowed("user-tb", 10, 60));
        assertTrue(leakyBucketRateLimiter.isAllowed("user-lb", 10, 60));

        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(0L);
        assertFalse(tokenBucketRateLimiter.isAllowed("user-tb", 10, 60));
        assertFalse(leakyBucketRateLimiter.isAllowed("user-lb", 10, 60));

        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenReturn(null);
        assertFalse(tokenBucketRateLimiter.isAllowed("user-tb", 10, 60));
        assertFalse(leakyBucketRateLimiter.isAllowed("user-lb", 10, 60));
    }

    @Test
    void testConcurrentRequestsRateLimiting() throws InterruptedException {
        int totalRequests = 20;
        int maxAllowed = 5;

        AtomicInteger simulatedRedisCounter = new AtomicInteger(0);

        when(redisTemplate.execute(any(), anyList(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    if (simulatedRedisCounter.incrementAndGet() <= maxAllowed) {
                        return 1L;
                    }
                    return 0L;
                });

        ExecutorService executorService = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger throttledCount = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executorService.submit(() -> {
                try {
                    latch.await();
                    int statusCode = mockMvc.perform(get("/api/token-bucket").header("X-API-Key", "concurrent-user"))
                            .andReturn()
                            .getResponse()
                            .getStatus();

                    if (statusCode == 200) {
                        successCount.incrementAndGet();
                    } else if (statusCode == 429) {
                        throttledCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        latch.countDown();
        executorService.shutdown();

        while (!executorService.isTerminated()) {
            Thread.sleep(20);
        }

        assertEquals(maxAllowed, successCount.get());
        assertEquals(totalRequests - maxAllowed, throttledCount.get());
    }
}