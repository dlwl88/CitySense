package com.hmdp.component;

import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.ISeckillVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

/**
 * 启动时将数据库中的秒杀库存同步到 Redis，避免旧数据没有经过新增接口时缺少库存 Key。
 */
@Component
@Slf4j
public class SeckillStockInitializer implements ApplicationRunner {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<SeckillVoucher> vouchers = seckillVoucherService.list();
            if (vouchers == null || vouchers.isEmpty()) {
                log.info("未查询到秒杀券数据，跳过秒杀库存预热");
                return;
            }
            for (SeckillVoucher voucher : vouchers) {
                if (voucher.getVoucherId() == null || voucher.getStock() == null) {
                    continue;
                }
                stringRedisTemplate.opsForValue().set(
                        SECKILL_STOCK_KEY + voucher.getVoucherId(),
                        voucher.getStock().toString()
                );
            }
            log.info("秒杀库存预热完成，共同步 {} 条库存记录", vouchers.size());
        } catch (Exception e) {
            log.error("秒杀库存预热失败", e);
        }
    }
}