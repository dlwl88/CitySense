package com.hmdp.utils;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class SlidingWindowLimitAspect {
    @Resource
    private RedissonClient redissonClient;
    private static final Logger log = LoggerFactory.getLogger(SlidingWindowLimitAspect.class);

    private static final String RATE_LIMIT_PREFIX = "rate_limit";

    @Around("@annotation(slidingWindowLimit)")
    public Object around(ProceedingJoinPoint joinPoint, SlidingWindowLimit slidingWindowLimit) throws Throwable {
        //获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getName();
        //判断是否为Http请求
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if(attributes == null){
            log.warn("方法 {} 不在 Web 请求上下文中，跳过限流检查。", methodName);
            return joinPoint.proceed();
        }
        try {
            Object parseSpEL = parseSpEL(joinPoint, signature, slidingWindowLimit.keySpEL());
            String rateLimitKey = buildRateLimitKey(parseSpEL, slidingWindowLimit);
            if(isRateLimited(rateLimitKey, slidingWindowLimit.windowSize(), slidingWindowLimit.timeUnit(), slidingWindowLimit.maxRequests())){
                throw new RateLimitExceededException("Rate limit exceeded");
            }
            return joinPoint.proceed();
        }catch (RateLimitExceededException e){
            log.warn("方法 {} 触发了限流，已拒绝访问。", methodName);
            throw e;
        }catch (Exception e){
            log.error("方法 {} 触发了异常，已拒绝访问。", methodName, e);
            throw e;
        }
    }

    private Object parseSpEL(ProceedingJoinPoint joinPoint,MethodSignature signature, String keySpEL){
        StandardEvaluationContext context = new StandardEvaluationContext();

        Object[] args = joinPoint.getArgs();
        String[] parameterNames = signature.getParameterNames();

        for (int i = 0; i < args.length; i++) {
            if(parameterNames!=null && i< parameterNames.length){
                context.setVariable(parameterNames[i], args[i]);
            }else {
                context.setVariable("arg" + i, args[i]);
            }
        }

        ExpressionParser parser = new SpelExpressionParser();
        return parser.parseExpression(keySpEL).getValue(context);
    }

    private String buildRateLimitKey(Object keyValue, SlidingWindowLimit slidingWindowLimit){
        if(keyValue == null){
            throw new IllegalArgumentException("限流参数不能为空");
        }
        
        // 判断是否开启全局限流
        if(slidingWindowLimit.globalLimit()){
            // 全局限流：所有用户共享一个限流桶
            return String.format("%s:global:%s:%s",
                    RATE_LIMIT_PREFIX,
                    slidingWindowLimit.keySpEL(),
                    keyValue);
        } else {
            // 用户级限流：每个用户独立限流
            Long userId = UserHolder.getUser().getId();
            return String.format("%s:%d:%s:%s",
                    RATE_LIMIT_PREFIX,
                    userId,
                    slidingWindowLimit.keySpEL(),
                    keyValue);
        }
    }

    private boolean isRateLimited(String key, int windowSize, TimeUnit timeUnit, int maxRequests) {
        // 1. 计算窗口大小（毫秒）
        long windowMillis = convertToMillis(windowSize, timeUnit);
        // 2. 获取当前时间和窗口起始时间
        long currentTime = System.currentTimeMillis();
        long windowStartTime = currentTime - windowMillis;
        // 3. 生成唯一请求ID（避免重复计数）
        String requestId = UUID.randomUUID().toString();
        // 4. 计算过期时间（秒），兜底为60秒，避免负数
        long expireSeconds = Math.max(calculateExpireSeconds(windowMillis), 60);

        // 5. 修复后的Lua脚本（核心：增加参数兜底+count类型转换）
        String luaScript =
                "-- 强制转换参数为浮点型，空值兜底\n" +
                        "local currentTime = tonumber(ARGV[1]) or 0.0\n" +
                        "local windowStartTime = tonumber(ARGV[2]) or 0.0\n" +
                        "local requestId = ARGV[3] or ''\n" +
                        "local maxRequests = tonumber(ARGV[4]) or 0.0\n" +
                        "local expireSeconds = tonumber(ARGV[5]) or 60.0\n" +
                        "\n" +
                        "-- 清理窗口外的过期请求\n" +
                        "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', windowStartTime)\n" +
                        "-- 统计窗口内请求数，转换为数字并兜底\n" +
                        "local count = tonumber(redis.call('ZCOUNT', KEYS[1], windowStartTime, currentTime)) or 0.0\n" +
                        "\n" +
                        "-- 限流判断（maxRequests为0时直接放行，避免误限流）\n" +
                        "if maxRequests > 0 and count >= maxRequests then\n" +
                        "    return 1\n" +  // 触发限流
                        "end\n" +
                        "\n" +
                        "-- 记录当前请求（分值为当前时间戳，确保浮点型）\n" +
                        "redis.call('ZADD', KEYS[1], currentTime, requestId)\n" +
                        "-- 设置过期时间（确保为正数）\n" +
                        "redis.call('EXPIRE', KEYS[1], math.max(expireSeconds, 60))\n" +
                        "return 0";  // 允许通过

        try {
            RScript script = redissonClient.getScript();
            Long result = script.eval(
                    RScript.Mode.READ_WRITE,
                    luaScript,
                    RScript.ReturnType.INTEGER,
                    Collections.singletonList(key),
                    (double) currentTime,  // 浮点型传递
                    (double) windowStartTime,
                    requestId,
                    (double) maxRequests,
                    (double) expireSeconds
            );
            // 返回true表示触发限流，false表示放行
            return result != null && result == 1;
        } catch (Exception e) {
            log.error("限流服务异常，降级放行，key:{}", key, e);
            return false; // Redis故障时允许请求通过
        }
    }
    private long calculateExpireSeconds(long windowMillis) {
        // 过期时间 = 2 * 窗口大小（秒），向上取整
        double expireSec = (windowMillis * 2.0) / 1000;
        long result = (long) Math.ceil(expireSec);
        return Math.max(1, result); // 至少1秒
    }


    /**
     * 时间单位转换，将时间单位转换为毫秒数
     * @param windowSize 窗口大小
     * @param timeUnit 时间单位
     * @return
     */
    private long convertToMillis(int windowSize, TimeUnit timeUnit){
        return switch (timeUnit){
            case NANOSECONDS, SECONDS, MICROSECONDS, MINUTES, HOURS, DAYS -> timeUnit.toMillis(windowSize);
            case MILLISECONDS -> windowSize;
            default -> throw new IllegalArgumentException("不支持的时间单位: " + timeUnit);
        };
    }
}


