package com.hmdp.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Resource;

@Configuration
@Slf4j
public class BloomFilterConfig {

    @Resource
    private RedissonClient redissonClient;

    // 布隆过滤器名称常量
    public static final String SHOP_BLOOM_FILTER = "bloom:shop";
    public static final String VOUCHER_BLOOM_FILTER = "bloom:voucher";
    // public static final String BLOG_BLOOM_FILTER = "bloom:blog"; // 如果暂时不用可以删除

    /**
     * 店铺布隆过滤器：只负责获取实例
     */
    @Bean
    public RBloomFilter<Long> shopBloomFilter() {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(SHOP_BLOOM_FILTER);
        log.info("✅ 店铺布隆过滤器Bean创建完成");
        // 🚨 删除了这里的 bloomFilter.tryInit(10000L, 0.01);
        return bloomFilter;
    }

    /**
     * 优惠券布隆过滤器：只负责获取实例
     */
    @Bean
    public RBloomFilter<Long> voucherBloomFilter() {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter(VOUCHER_BLOOM_FILTER);
        log.info("✅ 优惠券布隆过滤器Bean创建完成");
        // 🚨 删除了这里的 bloomFilter.tryInit(5000L, 0.01);
        return bloomFilter;
    }
}
