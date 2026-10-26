package com.cp.rlm.interceptor;

import com.cp.rlm.algorithms.LeakyBucketRateLimiter;
import com.cp.rlm.algorithms.TokenBucketRateLimiter;
import com.cp.rlm.annotation.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    public static final String RATE_LIMIT_CHECKED_ATTR = "RATE_LIMIT_CHECKED_ATTR";

    private final TokenBucketRateLimiter tokenBucketRateLimiter;
    private final LeakyBucketRateLimiter leakyBucketRateLimiter;

    public RateLimitInterceptor(TokenBucketRateLimiter tokenBucketRateLimiter,
                                LeakyBucketRateLimiter leakyBucketRateLimiter) {
        this.tokenBucketRateLimiter = tokenBucketRateLimiter;
        this.leakyBucketRateLimiter = leakyBucketRateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);

        if (rateLimit == null) {
            return true;
        }

        request.setAttribute(RATE_LIMIT_CHECKED_ATTR, Boolean.TRUE);

        String clientId = request.getHeader("X-API-Key");
        if (clientId == null || clientId.trim().isEmpty()) {
            clientId = request.getRemoteAddr();
        }
        boolean allowed;
        switch (rateLimit.algorithm()) {
            case LEAKY_BUCKET:
                allowed = leakyBucketRateLimiter.isAllowed(clientId, rateLimit.limit(), rateLimit.windowInSeconds());
                break;
            case TOKEN_BUCKET:
            default:
                allowed = tokenBucketRateLimiter.isAllowed(clientId, rateLimit.limit(), rateLimit.windowInSeconds());
                break;
        }

        if (!allowed) {

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");


            response.setHeader("Retry-After", String.valueOf(rateLimit.windowInSeconds()));

            String errorPayload = String.format(
                    "{\"error\": \"Too many requests. Limit exceeded.\", \"policy\": \"%s\", \"retry_after_seconds\": %d}",
                    rateLimit.algorithm().name(),
                    rateLimit.windowInSeconds()
            );

            response.getWriter().write(errorPayload);
            return false;
        }

        return true;
    }
}
