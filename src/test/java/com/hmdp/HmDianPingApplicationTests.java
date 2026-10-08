package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.service.impl.SeckillVoucherServiceImpl;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import jakarta.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;
import cn.hutool.core.lang.UUID;
@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private SeckillVoucherServiceImpl seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void createToken() throws IOException {
        PrintWriter printWriter = new PrintWriter(new FileWriter("D:\\tokens.txt"));
        for (int i = 0; i < 1000; i++) {
            // 1. 生成用户
            User user = new User();
            user.setPhone("1380000" + String.format("%04d", i));
            user.setNickName("user_" + i);
            user.setIcon("");

            // 🔥🔥🔥 关键修正：手动给一个 ID！否则 Redis 里没有 ID！🔥🔥🔥
            // 假设数据库里已经有了这些用户，或者我们只是模拟测试，给个假ID即可
            user.setId((long) (i + 1));

            // 2. 转为 UserDTO
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

            // 3. 生成 Token
            String token = UUID.randomUUID().toString(true);

            // 4. 存入 Redis
            String tokenKey = "login:token:" + token;

            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) -> {
                                if (fieldValue == null) {
                                    return null;
                                }
                                return fieldValue.toString();
                            }));

            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            // 设置有效期 30 分钟 (注意单位)
            stringRedisTemplate.expire(tokenKey, 30000000, TimeUnit.MINUTES);

            // 5. 写入文件
            printWriter.print(token + "\n");
        }
        printWriter.close();
        System.out.println("Token 生成完毕！");
    }
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    void loadShopData() {
        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            // 3.3.写入redis GEOADD key 经度 纬度 member
            for (Shop shop : value) {
                // stringRedisTemplate.opsForGeo().add(key, new Point(shop.getX(), shop.getY()), shop.getId().toString());
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if(j == 999){
                // 发送到Redis
                stringRedisTemplate.opsForHyperLogLog().add("hl2", values);
            }
        }
        // 统计数量
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println("count = " + count);
    }

    @Test
    void loadSeckillVoucher() {
        List<SeckillVoucher> list = seckillVoucherService.list();
        if (list == null || list.isEmpty()) {
            System.out.println("没有秒杀优惠券数据");
            return;
        }
        for (SeckillVoucher voucher : list) {
            String stockKey = "seckill:stock:" + voucher.getVoucherId();
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(voucher.getStock()));
            System.out.println("预热优惠券库存成功，voucherId=" + voucher.getVoucherId() + "，stock=" + voucher.getStock());
        }
        System.out.println("所有秒杀优惠券库存预热完成，共 " + list.size() + " 条");
    }
}
