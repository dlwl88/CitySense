package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IPaymentService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.hmdp.utils.RedisConstants.ORDER_LOCK_PREFIX;
import static com.hmdp.utils.RedisConstants.SECKILL_ORDER_PENDING_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements IPaymentService {

    private final IVoucherOrderService voucherOrderService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final PlatformTransactionManager transactionManager;

    @Override
    public Result createPayment(Long orderId, Integer payType) {
        return withOrderLock(orderId, () -> {
            VoucherOrder order = voucherOrderService.getById(orderId);
            if (order == null) {
                return Result.fail("订单不存在");
            }
            if (!VoucherOrder.STATUS_UNPAID.equals(order.getStatus())) {
                return Result.fail("订单状态不正确");
            }
            boolean updated = voucherOrderService.update()
                    .set("pay_type", payType)
                    .eq("id", orderId)
                    .eq("status", VoucherOrder.STATUS_UNPAID)
                    .update();
            if (!updated) {
                return Result.fail("订单状态已变化，请刷新后重试");
            }
            log.info("创建支付订单：订单ID={}, 支付方式={}", orderId, payType);
            return Result.ok("支付订单创建成功");
        });
    }

    @Override
    public Result simulatePaymentCallback(Long orderId) {
        return handlePaymentCallback(orderId);
    }

    @Override
    public Result wechatPayCallback(Long orderId) {
        log.info("收到微信支付回调：订单ID={}", orderId);
        return handlePaymentCallback(orderId);
    }

    @Override
    public Result alipayCallback(Long orderId) {
        log.info("收到支付宝支付回调：订单ID={}", orderId);
        return handlePaymentCallback(orderId);
    }

    private Result handlePaymentCallback(Long orderId) {
        return withOrderLock(orderId, () -> {
            VoucherOrder order = voucherOrderService.getById(orderId);
            if (order == null) {
                return Result.fail("订单不存在");
            }

            if (VoucherOrder.STATUS_PAID.equals(order.getStatus())) {
                log.info("订单已支付，订单ID={}", orderId);
                return Result.ok("订单已支付");
            }

            if (!VoucherOrder.STATUS_UNPAID.equals(order.getStatus())) {
                log.warn("订单状态不正确，无法支付，订单ID={}, 状态={}", orderId, order.getStatus());
                return Result.fail("订单状态不正确");
            }

            LocalDateTime now = LocalDateTime.now();
            boolean updated = voucherOrderService.update()
                    .set("status", VoucherOrder.STATUS_PAID)
                    .set("pay_time", now)
                    .set("update_time", now)
                    .eq("id", orderId)
                    .eq("status", VoucherOrder.STATUS_UNPAID)
                    .update();
            if (!updated) {
                return Result.fail("订单状态已变化，请刷新后重试");
            }

            log.info("订单支付成功，订单ID={}", orderId);
            return Result.ok("支付成功");
        });
    }

    private Result withOrderLock(Long orderId, Supplier<Result> action) {
        RLock lock = redissonClient.getLock(ORDER_LOCK_PREFIX + orderId);
        boolean acquired = false;
        try {
            // 不指定租期，使用 Redisson watchdog 自动续期。
            acquired = lock.tryLock(2, TimeUnit.SECONDS);
            if (!acquired) {
                return Result.fail("订单处理中，请稍后重试");
            }
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            // execute 返回时事务已提交或回滚，再释放订单锁。
            return transaction.execute(status -> action.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.fail("订单处理被中断，请稍后重试");
        } catch (Exception e) {
            log.error("订单支付处理异常，订单ID={}", orderId, e);
            return Result.fail("订单支付处理失败");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Result checkPaymentStatus(Long orderId) {
        VoucherOrder order = voucherOrderService.getById(orderId);
        if (order != null) {
            return Result.ok(order.getStatus());
        }

        UserDTO user = UserHolder.getUser();
        String pendingUserId = stringRedisTemplate.opsForValue().get(SECKILL_ORDER_PENDING_KEY + orderId);
        if (!StringUtils.hasText(pendingUserId)) {
            return Result.fail("订单不存在");
        }
        if (user == null || user.getId() == null || !pendingUserId.equals(String.valueOf(user.getId()))) {
            return Result.fail("订单不属于当前用户");
        }
        return Result.ok(0);
    }
}
