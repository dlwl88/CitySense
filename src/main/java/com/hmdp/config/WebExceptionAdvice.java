package com.hmdp.config;

import com.hmdp.dto.Result;
import com.hmdp.utils.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RateLimitExceededException.class)
    public Result handleRateLimitExceededException(RateLimitExceededException e) {
        log.warn("请求触发限流: {}", e.getMessage());
        return Result.fail("AI 请求过于频繁，请稍后再试");
    }

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }
}