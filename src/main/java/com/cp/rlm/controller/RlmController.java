package com.cp.rlm.controller;

import com.cp.rlm.algorithms.RateLimitAlgorithm;
import com.cp.rlm.annotation.RateLimit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class RlmController {
    @GetMapping("/token-bucket")
    @RateLimit(limit = 10, windowInSeconds = 60, algorithm = RateLimitAlgorithm.TOKEN_BUCKET)
    public ResponseEntity<Map<String, String>> getTokenBucketResource(){
        return ResponseEntity.ok(Map.of(
                "status","Success",
                "message", "Accessed token bucket protected endpoint."
        ));
    }
    @GetMapping("/leaky-bucket")
    @RateLimit(limit = 10, windowInSeconds = 60, algorithm = RateLimitAlgorithm.LEAKY_BUCKET)
    public ResponseEntity<Map<String, String>> getLeakyBucketResource() {
        return ResponseEntity.ok(Map.of(
                "status", "Success",
                "message", "Accessed leaky bucket protected endpoint."
        ));
    }
}
