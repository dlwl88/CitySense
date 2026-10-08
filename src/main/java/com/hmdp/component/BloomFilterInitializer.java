package com.hmdp.component;

import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.VoucherMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class BloomFilterInitializer implements ApplicationRunner {

    @Resource
    private RBloomFilter<Long> shopBloomFilter;

    @Resource
    private RBloomFilter<Long> voucherBloomFilter;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private VoucherMapper voucherMapper;

    @Override
    public void run(ApplicationArguments args) {
        log.info("========== 开始预热布隆过滤器 ==========");
        // 异步执行
        this.initShopBloomFilter();
        this.initVoucherBloomFilter();
    }

    private void initShopBloomFilter() {
        try {
            long startTime = System.currentTimeMillis();

            // 1. 【重要】初始化前先删除旧Key，防止包含已删除的数据
            shopBloomFilter.delete();
            // 2. 【重要】初始化：预计插入10万条，误判率1%
            shopBloomFilter.tryInit(100000L, 0.01);

            int pageSize = 1000;
            int pageNum = 0;
            List<Long> shopIds;
            do {
                shopIds = shopMapper.selectAllIds(pageNum * pageSize, pageSize);
                for (Long id : shopIds) {
                    shopBloomFilter.add(id);
                }
                pageNum++;
            } while (shopIds.size() == pageSize);

            log.info("店铺布隆过滤器预热完成，耗时 {} ms", System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("店铺布隆过滤器预热失败", e);
        }
    }

    private void initVoucherBloomFilter() {
        try {
            long startTime = System.currentTimeMillis();

            // 1. 清理旧数据
            voucherBloomFilter.delete();
            // 2. 初始化：预计插入1万条，误判率1%
            voucherBloomFilter.tryInit(10000L, 0.01);

            // 修正：使用 voucherMapper 且同样采用分页（更安全）
            int pageSize = 1000;
            int pageNum = 0;
            List<Long> voucherIds;
            do {
                // 修正：调用 voucherMapper 的方法
                voucherIds = voucherMapper.selectAllIds(pageNum * pageSize, pageSize);
                for (Long id : voucherIds) {
                    voucherBloomFilter.add(id);
                }
                pageNum++;
            } while (voucherIds.size() == pageSize);

            log.info("优惠券布隆过滤器预热完成，耗时 {} ms", System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            log.error("优惠券布隆过滤器预热失败", e);
        }
    }
}
