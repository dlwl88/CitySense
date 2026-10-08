package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.UpdateChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mq.OrderTimeoutListener;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.impl.PaymentServiceImpl;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static com.hmdp.utils.RedisConstants.ORDER_LOCK_PREFIX;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class NormalTest {

    @Test
    void testBitMap() {
        int i = 0b1110111111111111111111111;

        long t1 = System.nanoTime();
        int count = 0;
        while (true){
            if ((i & 1) == 0){
                    break;
            }else{
                count++;
            }
            i >>>= 1;
        }
        long t2 = System.nanoTime();
        System.out.println("time1 = " + (t2 - t1));
        System.out.println("count = " + count);

        i = 0b1110111111111111111111111;
        long t3 = System.nanoTime();
        int count2 = 0;
        while (true) {
            if(i >>> 1 << 1 == i){
                // 未签到，结束
                break;
            }else{
                // 说明签到了
                count2++;
            }

            i >>>= 1;
        }
        long t4 = System.nanoTime();
        System.out.println("time2 = " + (t4 - t3));
        System.out.println("count2 = " + count2);
    }

    @Nested
    class OrderConcurrencyTests {

        @Test
        void paymentWinningRaceMustPreventCancellationAndStockRestore() throws Exception {
            runRace(true);
        }

        @Test
        void timeoutWinningRaceMustRejectPayment() throws Exception {
            runRace(false);
        }

        private void runRace(boolean paymentFirst) throws Exception {
            OrderFixture fixture = new OrderFixture();
            fixture.pauseFirstUpdate = true;
            var executor = Executors.newFixedThreadPool(2);
            try {
                var first = executor.submit(() -> {
                    if (paymentFirst) return fixture.payment.simulatePaymentCallback(1L);
                    fixture.orders.closeTimeoutOrder(1L);
                    return Result.ok();
                });
                assertTrue(fixture.updateEntered.await(5, TimeUnit.SECONDS));
                var second = executor.submit(() -> {
                    if (!paymentFirst) return fixture.payment.simulatePaymentCallback(1L);
                    fixture.orders.closeTimeoutOrder(1L);
                    return Result.ok();
                });
                assertTrue(fixture.secondLockAttempt.await(5, TimeUnit.SECONDS));
                assertFalse(second.isDone(), "同一订单的第二个操作必须等待锁");
                fixture.releaseUpdate.countDown();
                assertTrue(first.get(5, TimeUnit.SECONDS).getSuccess());
                assertEquals(paymentFirst, second.get(5, TimeUnit.SECONDS).getSuccess());
                assertEquals(paymentFirst ? VoucherOrder.STATUS_PAID : VoucherOrder.STATUS_CANCELLED,
                        fixture.order.getStatus());
                assertEquals(1, fixture.dbUpdates.get());
                assertEquals(paymentFirst ? 0 : 1, fixture.stockRestores.get());
                assertEquals(paymentFirst ? 0 : 1, fixture.redisCalls.get());
                verify(fixture.redisson, times(2)).getLock(ORDER_LOCK_PREFIX + 1L);
                assertEquals(List.of("commit", "unlock", "commit", "unlock"), fixture.events);
            } finally {
                fixture.releaseUpdate.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }

        @Test
        void duplicatePaymentCallbacksMustUpdateOnlyOnce() throws Exception {
            OrderFixture fixture = new OrderFixture();
            assertTrue(fixture.payment.wechatPayCallback(1L).getSuccess());
            assertTrue(fixture.payment.alipayCallback(1L).getSuccess());
            assertEquals(1, fixture.dbUpdates.get());
            assertEquals(VoucherOrder.STATUS_PAID, fixture.order.getStatus());
        }

        @Test
        void duplicateTimeoutMessagesMustRestoreDatabaseStockOnlyOnce() throws Exception {
            OrderFixture fixture = new OrderFixture();
            fixture.orders.closeTimeoutOrder(1L);
            fixture.orders.closeTimeoutOrder(1L);
            assertEquals(1, fixture.dbUpdates.get());
            assertEquals(1, fixture.stockRestores.get());
        }

        @Test
        void lockContentionMustRejectPaymentAndRetryTimeoutMessage() throws Exception {
            OrderFixture fixture = new OrderFixture();
            doReturn(false).when(fixture.lock).tryLock(2, TimeUnit.SECONDS);
            assertFalse(fixture.payment.simulatePaymentCallback(1L).getSuccess());
            assertThrows(IllegalStateException.class,
                    () -> new OrderTimeoutListener(fixture.orders).onMessage("1"));
            verify(fixture.orders, never()).getById(any());
            verifyNoInteractions(fixture.transactions);
            verify(fixture.lock, never()).unlock();
        }

        @Test
        void redisFailureMustRetryCompensationWithoutRestoringDatabaseStockAgain() throws Exception {
            OrderFixture fixture = new OrderFixture();
            doThrow(new IllegalStateException("Redis unavailable")).doReturn(1L)
                    .when(fixture.redis).execute(any(RedisScript.class), anyList(), any(Object[].class));
            OrderTimeoutListener listener = new OrderTimeoutListener(fixture.orders);
            assertThrows(IllegalStateException.class, () -> listener.onMessage("1"));
            listener.onMessage("1");
            assertEquals(VoucherOrder.STATUS_CANCELLED, fixture.order.getStatus());
            assertEquals(1, fixture.dbUpdates.get());
            assertEquals(1, fixture.stockRestores.get());
            verify(fixture.lock, times(2)).unlock();
        }

        @Test
        void databaseFailureMustRollbackBeforeUnlockAndSkipRedisCompensation() throws Exception {
            OrderFixture fixture = new OrderFixture();
            when(fixture.stockUpdate.update()).thenReturn(false);
            assertThrows(IllegalStateException.class, () -> fixture.orders.closeTimeoutOrder(1L));
            assertEquals(List.of("rollback", "unlock"), fixture.events);
            assertEquals(0, fixture.redisCalls.get());
        }

        @Test
        void commitFailureMustNotReportPaymentSuccess() throws Exception {
            OrderFixture fixture = new OrderFixture();
            doThrow(new IllegalStateException("Commit failed")).when(fixture.transactions).commit(any());
            assertFalse(fixture.payment.simulatePaymentCallback(1L).getSuccess());
            verify(fixture.lock).unlock();
        }

        @Test
        void creatingPaymentMustUpdateOnlyPaymentTypeAndRespectCancelledState() throws Exception {
            OrderFixture fixture = new OrderFixture();
            assertTrue(fixture.payment.createPayment(1L, VoucherOrder.PAY_TYPE_WECHAT).getSuccess());
            assertEquals(VoucherOrder.STATUS_UNPAID, fixture.order.getStatus());
            assertEquals(VoucherOrder.PAY_TYPE_WECHAT, fixture.order.getPayType());
            fixture.orders.closeTimeoutOrder(1L);
            assertFalse(fixture.payment.createPayment(1L, VoucherOrder.PAY_TYPE_ALIPAY).getSuccess());
            assertEquals(VoucherOrder.STATUS_CANCELLED, fixture.order.getStatus());
        }
    }

    // 在内存中模拟订单存储和共享锁，不启动外部服务、不改写业务数据。
    private static class OrderFixture {
        final VoucherOrder order = new VoucherOrder().setId(1L).setUserId(2L)
                .setVoucherId(3L).setStatus(VoucherOrder.STATUS_UNPAID);
        final VoucherOrderServiceImpl orders = spy(new VoucherOrderServiceImpl());
        final StringRedisTemplate redis = mock(StringRedisTemplate.class);
        final RedissonClient redisson = mock(RedissonClient.class);
        final RLock lock = mock(RLock.class);
        final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        final UpdateChainWrapper<SeckillVoucher> stockUpdate = mock(UpdateChainWrapper.class, RETURNS_SELF);
        final AtomicInteger dbUpdates = new AtomicInteger();
        final AtomicInteger stockRestores = new AtomicInteger();
        final AtomicInteger redisCalls = new AtomicInteger();
        final List<String> events = new ArrayList<>();
        final CountDownLatch updateEntered = new CountDownLatch(1);
        final CountDownLatch releaseUpdate = new CountDownLatch(1);
        final CountDownLatch secondLockAttempt = new CountDownLatch(1);
        final PaymentServiceImpl payment;
        boolean pauseFirstUpdate;

        OrderFixture() throws Exception {
            ReentrantLock mutex = new ReentrantLock();
            AtomicInteger attempts = new AtomicInteger();
            when(redisson.getLock(ORDER_LOCK_PREFIX + 1L)).thenReturn(lock);
            when(lock.tryLock(2, TimeUnit.SECONDS)).thenAnswer(invocation -> {
                if (attempts.incrementAndGet() == 2) secondLockAttempt.countDown();
                return mutex.tryLock(2, TimeUnit.SECONDS);
            });
            when(lock.isHeldByCurrentThread()).thenAnswer(invocation -> mutex.isHeldByCurrentThread());
            doAnswer(invocation -> {
                events.add("unlock");
                mutex.unlock();
                return null;
            }).when(lock).unlock();
            when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
            doAnswer(invocation -> { events.add("commit"); return null; }).when(transactions).commit(any());
            doAnswer(invocation -> { events.add("rollback"); return null; }).when(transactions).rollback(any());
            doAnswer(invocation -> new VoucherOrder().setId(1L).setUserId(2L).setVoucherId(3L)
                    .setStatus(order.getStatus()).setPayType(order.getPayType())).when(orders).getById(1L);

            VoucherOrderMapper mapper = mock(VoucherOrderMapper.class);
            when(mapper.update(nullable(VoucherOrder.class), any(Wrapper.class))).thenAnswer(invocation -> {
                UpdateWrapper<VoucherOrder> update = invocation.getArgument(1);
                assertTrue(update.getSqlSegment().contains("status"), "更新必须附带未支付状态条件");
                assertTrue(update.getParamNameValuePairs().containsValue(VoucherOrder.STATUS_UNPAID));
                assertTrue(update.getSqlSegment().contains("id"));
                if (pauseFirstUpdate && dbUpdates.get() == 0) {
                    updateEntered.countDown();
                    assertTrue(releaseUpdate.await(5, TimeUnit.SECONDS));
                }
                if (!VoucherOrder.STATUS_UNPAID.equals(order.getStatus())) return 0;
                var matcher = Pattern.compile("(status|pay_type)\\s*=\\s*#\\{ew\\.paramNameValuePairs\\.(\\w+)\\}")
                        .matcher(update.getSqlSet());
                assertTrue(matcher.find());
                Integer value = (Integer) update.getParamNameValuePairs().get(matcher.group(2));
                if ("status".equals(matcher.group(1))) order.setStatus(value);
                else order.setPayType(value);
                dbUpdates.incrementAndGet();
                return 1;
            });
            ISeckillVoucherService stock = mock(ISeckillVoucherService.class);
            when(stock.update()).thenReturn(stockUpdate);
            doReturn(stockUpdate).when(stockUpdate).setSql(anyString(), any(Object[].class));
            doReturn(stockUpdate).when(stockUpdate).eq(anyString(), any());
            when(stockUpdate.update()).thenAnswer(invocation -> { stockRestores.incrementAndGet(); return true; });
            when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                    .thenAnswer(invocation -> { redisCalls.incrementAndGet(); return 1L; });
            ReflectionTestUtils.setField(orders, "baseMapper", mapper);
            ReflectionTestUtils.setField(orders, "redissonClient", redisson);
            ReflectionTestUtils.setField(orders, "transactionManager", transactions);
            ReflectionTestUtils.setField(orders, "stringRedisTemplate", redis);
            ReflectionTestUtils.setField(orders, "seckillVoucherService", stock);
            payment = new PaymentServiceImpl(orders, redis, redisson, transactions);
        }
    }
}
