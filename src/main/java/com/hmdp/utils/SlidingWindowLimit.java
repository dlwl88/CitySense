package com.hmdp.utils;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target({ElementType.METHOD,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SlidingWindowLimit {
    /**
     * 限流维度的 SpEL 表达式
     */
    String keySpEL() default "#request.remoteAddr";
    /**
     * 窗口大小
     */
    int windowSize() default 1;
    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;
    /**
     * 窗口内最大请求数
     */
    int maxRequests() default 1000;
    /**
     * 是否开启全局限流
     * true: 所有用户共享一个限流桶（全局限流）
     * false: 每个用户独立限流（用户级限流）
     */
    boolean globalLimit() default false;

}

