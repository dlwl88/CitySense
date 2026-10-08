package com.hmdp.job;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** 显式启用后连接本机 Redis，只操作本测试随机命名的 key。 */
@EnabledIfSystemProperty(named = "like.redis.integration", matches = "true")
class BlogLikeLuaIntegrationTest {
    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static final List<String> keys = List.of(
            "test:blog-like:" + UUID.randomUUID() + ":users",
            "test:blog-like:" + UUID.randomUUID() + ":meta",
            "test:blog-like:" + UUID.randomUUID() + ":dirty");
    private static DefaultRedisScript<Long> like;
    private static DefaultRedisScript<Long> ack;

    @BeforeAll
    static void connect() {
        factory = new LettuceConnectionFactory("127.0.0.1", 6379);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        like = load("blog_like.lua");
        ack = load("blog_like_ack.lua");
    }

    private static DefaultRedisScript<Long> load(String name) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(name));
        script.setResultType(Long.class);
        return script;
    }

    @BeforeEach
    void reset() {
        redis.delete(keys);
    }

    @AfterAll
    static void disconnect() {
        if (redis != null) redis.delete(keys);
        if (factory != null) factory.destroy();
    }

    private void initialize(int count, String version) {
        redis.opsForHash().putAll(keys.get(1), Map.of("count", Integer.toString(count), "version", version));
    }

    private Long toggle(String user) {
        return redis.execute(like, keys, user, "123", "1000");
    }

    @Test
    void missingMetadataLeavesAllStateUntouched() {
        assertEquals(-1L, toggle("7"));
        for (String key : keys) assertFalse(Boolean.TRUE.equals(redis.hasKey(key)));
    }

    @Test
    void preservesHistoricalBaselineAndSwitchesBack() {
        initialize(13, "0");
        assertEquals(1L, toggle("7"));
        assertEquals("14", redis.opsForHash().get(keys.get(1), "count"));
        assertEquals("1", redis.opsForHash().get(keys.get(2), "123"));
        assertEquals(1L, toggle("7"));
        assertEquals("13", redis.opsForHash().get(keys.get(1), "count"));
        assertEquals("2", redis.opsForHash().get(keys.get(2), "123"));
        assertEquals(0L, redis.opsForZSet().size(keys.get(0)));
    }

    @Test
    void staleAcknowledgementKeepsNewLike() {
        initialize(0, "0");
        toggle("7"); // 数据库读取了 version=1 的快照。
        toggle("8"); // 数据库提交期间又发生新点赞。
        assertEquals(0L, redis.execute(ack, List.of(keys.get(2)), "123", "1"));
        assertEquals("2", redis.opsForHash().get(keys.get(2), "123"));
        assertEquals(1L, redis.execute(ack, List.of(keys.get(2)), "123", "2"));
        assertFalse(redis.opsForHash().hasKey(keys.get(2), "123"));
    }

    @Test
    void concurrentDistinctUsersAndRepeatedTogglesKeepCountConsistent() throws Exception {
        initialize(0, "0");
        var pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Long>> actions = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                String user = Integer.toString(i);
                actions.add(() -> toggle(user));
            }
            for (var future : pool.invokeAll(actions, 15, TimeUnit.SECONDS)) {
                assertEquals(1L, future.get());
            }
            assertEquals("200", redis.opsForHash().get(keys.get(1), "count"));
            actions.clear();
            for (int i = 0; i < 100; i++) actions.add(() -> toggle("0"));
            for (var future : pool.invokeAll(actions, 15, TimeUnit.SECONDS)) {
                assertEquals(1L, future.get());
            }
            assertEquals("200", redis.opsForHash().get(keys.get(1), "count"));
            assertEquals("300", redis.opsForHash().get(keys.get(1), "version"));
            assertEquals(200L, redis.opsForZSet().size(keys.get(0)));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void versionsAboveLuaExactIntegerRangeStayExact() {
        initialize(0, "9007199254740992");
        toggle("7");
        assertEquals("9007199254740993", redis.opsForHash().get(keys.get(2), "123"));
    }

    @Test
    void invalidDecreaseDoesNotMutateState() {
        initialize(0, "0");
        redis.opsForZSet().add(keys.get(0), "7", 1000);
        assertEquals(-2L, toggle("7"));
        assertNotNull(redis.opsForZSet().score(keys.get(0), "7"));
        assertEquals("0", redis.opsForHash().get(keys.get(1), "version"));
        assertFalse(Boolean.TRUE.equals(redis.hasKey(keys.get(2))));
    }
}
