package com.hmdp;

import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;
@SpringBootTest
public class BoomFilterTest {
    @Resource
    private RBloomFilter<Long> shopBloomFilter;

    @Test
    void testBoomFilterTest(){
        // 1. 测试一个数据库中存在的 ID (假设 ID 1 是存在的)
        boolean exists = shopBloomFilter.contains(1L);
        System.out.println("ID=1 是否存在: " + exists); // 应该输出 true

        // 2. 测试一个绝对不存在的 ID (例如负数或巨大的数)
        boolean notExists = shopBloomFilter.contains(999999999L);
        System.out.println("ID=999999999 是否存在: " + notExists); // 应该输出 false
    }
}
