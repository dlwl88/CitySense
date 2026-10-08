package com.hmdp.config;

import java.util.concurrent.TimeUnit;

public final class RocketMQConstants {

    private RocketMQConstants() {
    }

    public static final String SECKILL_ORDER_TOPIC = "seckill-order-topic";
    public static final String SECKILL_ORDER_TAG = "create";
    public static final String SECKILL_ORDER_DESTINATION = SECKILL_ORDER_TOPIC + ":" + SECKILL_ORDER_TAG;
    public static final String SECKILL_ORDER_CONSUMER_GROUP = "hmdp-seckill-order-consumer";

    public static final String ORDER_TIMEOUT_TOPIC = "order-timeout-topic";
    public static final String ORDER_TIMEOUT_TAG = "close";
    public static final String ORDER_TIMEOUT_DESTINATION = ORDER_TIMEOUT_TOPIC + ":" + ORDER_TIMEOUT_TAG;
    public static final String ORDER_TIMEOUT_CONSUMER_GROUP = "hmdp-order-timeout-consumer";

    public static final long ORDER_PAYMENT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(15);
}
