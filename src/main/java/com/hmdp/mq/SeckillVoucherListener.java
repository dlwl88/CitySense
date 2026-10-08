package com.hmdp.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.config.RocketMQConstants;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@RocketMQMessageListener(
        consumerGroup = RocketMQConstants.SECKILL_ORDER_CONSUMER_GROUP,
        topic = RocketMQConstants.SECKILL_ORDER_TOPIC,
        selectorExpression = RocketMQConstants.SECKILL_ORDER_TAG,
        consumeMode = ConsumeMode.CONCURRENTLY,
        messageModel = MessageModel.CLUSTERING
)
public class SeckillVoucherListener implements RocketMQListener<String> {

    private final IVoucherOrderService voucherOrderService;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            log.info("收到秒杀订单消息：{}", message);
            VoucherOrder voucherOrder = objectMapper.readValue(message, VoucherOrder.class);
            voucherOrderService.createVoucherOrder(voucherOrder);
        } catch (Exception e) {
            log.error("消费秒杀订单消息失败，message={}", message, e);
            throw new IllegalStateException("消费秒杀订单消息失败", e);
        }
    }
}