package com.hmdp.utils;

/**
 * 限流异常类
 * 当请求超过限流阈值时抛出此异常
 */
public class RateLimitExceededException extends RuntimeException {
    
    public RateLimitExceededException() {
        super();
    }
    
    public RateLimitExceededException(String message) {
        super(message);
    }
    
    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public RateLimitExceededException(Throwable cause) {
        super(cause);
    }
}
