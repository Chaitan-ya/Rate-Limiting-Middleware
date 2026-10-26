# Rate Limiting Middleware (RLM)

A distributed, high-performance Rate Limiting Middleware built with **Spring Boot 4 / Java 21** and **Redis**.

This project provides distributed API rate limiting using atomic Redis Lua scripts to protect services against burst traffic, resource exhaustion, and abuse.

---

## Features

- **Distributed Rate Limiting with Redis Lua Scripts**: All bucket replenishment, water leakage, and limit evaluation happen atomically inside Redis in a single round-trip.
- **Multiple Supported Algorithms**:
  - **Token Bucket**: Allows burst traffic up to bucket capacity while refilling tokens continuously at a steady rate.
  - **Leaky Bucket**: Enforces a constant outflow rate and smooths traffic bursts by tracking water level and continuous drainage over time.
- **Annotation-Driven Architecture**: Easily protect endpoints using `@RateLimit(limit = ..., windowInSeconds = ..., algorithm = ...)`.
- **Hybrid Enforcement**:
  - **HandlerInterceptor**: Evaluates limits early in the MVC request lifecycle and responds with HTTP `429 Too Many Requests` + `Retry-After` header.
  - **AOP Aspect**: Protects service/controller methods with collision prevention against duplicate evaluations.
- **Fail-Open Resilience**: If Redis is temporarily unreachable or experiencing an outage, requests fail open gracefully with error logging rather than bringing down business-critical APIs.
- **Client Identification**: Automatically tracks client identity using `X-API-Key` HTTP header with automatic fallback to remote IP address (`request.getRemoteAddr()`).

---

## Architecture Overview

```
                      +-----------------------------+
                      |       Client Request        |
                      +--------------+--------------+
                                     |
                                     v
                      +-----------------------------+
                      |    RateLimitInterceptor     |
                      |  - Checks @RateLimit        |
                      |  - Identifies client        |
                      +--------------+--------------+
                                     |
                                     v
                       +---------------------------+
                       |    Redis Atomic Script    |
                       | (Token Bucket/LeakyBucket)|
                       +-------------+-------------+
                                     |
                     +---------------+---------------+
                     |                               |
              [Allowed (1)]                   [Throttled (0)]
                     |                               |
                     v                               v
           Proceed to Controller           Return HTTP 429 Too Many
                                             (JSON payload + Retry-After)
```

---

## Algorithms

### 1. Token Bucket (`RateLimitAlgorithm.TOKEN_BUCKET`)
- Requests consume tokens from a bucket with a maximum capacity.
- Tokens refill continuously over time based on $\text{capacity} / \text{windowInSeconds}$.
- Accommodates short, legitimate bursts up to bucket capacity while bounding average throughput.

### 2. Leaky Bucket (`RateLimitAlgorithm.LEAKY_BUCKET`)
- Requests add water to a bucket with a maximum capacity.
- Water leaks out at a constant rate over time ($\text{capacity} / \text{windowInSeconds}$).
- If water level exceeds capacity, excess requests overflow and are throttled.
- Eliminates traffic bursts and enforces smooth, predictable request rates.

---

## Usage

### 1. Annotating Endpoints

```java
@RestController
@RequestMapping("/api")
public class ApiController {

    // Protect using Token Bucket: 10 requests per 60 seconds
    @GetMapping("/token-bucket")
    @RateLimit(limit = 10, windowInSeconds = 60, algorithm = RateLimitAlgorithm.TOKEN_BUCKET)
    public ResponseEntity<Map<String, String>> getTokenBucketProtectedResource() {
        return ResponseEntity.ok(Map.of("status", "Success"));
    }

    // Protect using Leaky Bucket: 10 requests per 60 seconds
    @GetMapping("/leaky-bucket")
    @RateLimit(limit = 10, windowInSeconds = 60, algorithm = RateLimitAlgorithm.LEAKY_BUCKET)
    public ResponseEntity<Map<String, String>> getLeakyBucketProtectedResource() {
        return ResponseEntity.ok(Map.of("status", "Success"));
    }
}
```

---

## Configuration

In `src/main/resources/application.properties`:

```properties
spring.application.name=rlm
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

---

## Running the Application

### 1. Start Redis
```bash
docker run -d --name redis-rate-limiter -p 6379:6379 redis:alpine
```

### 2. Run the Application
```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

### 3. Run the Tests
```bash
.\mvnw.cmd test
```

---

## Throttling Response Example

When the rate limit is exceeded, the client receives:

**Status**: `429 Too Many Requests`  
**Headers**: `Retry-After: 60`  
**Body**:
```json
{
  "error": "Too many requests. Limit exceeded.",
  "policy": "TOKEN_BUCKET",
  "retry_after_seconds": 60
}
```
