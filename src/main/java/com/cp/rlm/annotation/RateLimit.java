package com.cp.rlm.annotation;

import com.cp.rlm.algorithms.RateLimitAlgorithm;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    long limit() default 10;
    long windowInSeconds() default 60;
    RateLimitAlgorithm algorithm() default RateLimitAlgorithm.TOKEN_BUCKET;
}
