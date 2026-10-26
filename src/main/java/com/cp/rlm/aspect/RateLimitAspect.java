package com.cp.rlm.aspect;

import com.cp.rlm.annotation.RateLimit;
import com.cp.rlm.algorithms.LeakyBucketRateLimiter;
import com.cp.rlm.algorithms.TokenBucketRateLimiter;
import com.cp.rlm.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.cp.rlm.interceptor.RateLimitInterceptor;
import jakarta.servlet.http.HttpServletRequest;

@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final TokenBucketRateLimiter tokenBucketRateLimiter;
    private final LeakyBucketRateLimiter leakyBucketRateLimiter;

    @Around("@annotation(rateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {

        String clientId = "global";
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            if (Boolean.TRUE.equals(request.getAttribute(RateLimitInterceptor.RATE_LIMIT_CHECKED_ATTR))) {
                // Already enforced by RateLimitInterceptor for this HTTP request
                return joinPoint.proceed();
            }
            clientId = request.getHeader("X-API-Key");
            if (clientId == null || clientId.trim().isEmpty()) {
                clientId = request.getRemoteAddr();
            }
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
            throw new RateLimitException("Rate limit exceeded. Too many structural executions.");
        }

        return joinPoint.proceed();
    }
}