package com.jupin.server.mq;

import cn.hutool.json.JSONUtil;
import com.jupin.server.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TimeoutProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(TimeoutMessage message, long delayMillis) {
        // 对延迟时间做下限保护。
        // 即使调用方传入 0 或负数，也至少延迟 1 秒，避免消息刚发送就立刻进入死信队列。
        long safeDelay = Math.max(delayMillis, 1000L);
        // 发送消息到超时延迟交换机。
        // RabbitMQ 根据 routingKey 把消息投递到对应的延迟队列。
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_TIMEOUT_DELAY,
                resolveRoutingKey(message),
                // 将超时消息对象序列化为 JSON，消费者收到后再反序列化。
                JSONUtil.toJsonStr(message),
                msg -> {
                    // 设置消息级 TTL。
                    // 消息在延迟队列中存活 safeDelay 毫秒后过期，过期后变成死信。
                    msg.getMessageProperties().setExpiration(String.valueOf(safeDelay));
                    return msg;
                });
    }

    private String resolveRoutingKey(TimeoutMessage message) {
        // 消息为空或类型为空时，默认走确认超时队列，避免空类型导致发送失败。
        if (message == null || message.getType() == null) {
            return RabbitConfig.ROUTING_TIMEOUT_CONFIRM_DELAY;
        }
        switch (message.getType()) {
            case TimeoutMessage.ORDER_DEPOSIT_PAYMENT:
            case TimeoutMessage.ORDER_PAYMENT:
                // 押金支付超时消息进入押金延迟队列。
                return RabbitConfig.ROUTING_TIMEOUT_ORDER_DEPOSIT_DELAY;
            case TimeoutMessage.ORDER_FINAL_PAYMENT:
                // 尾款支付超时消息进入尾款延迟队列。
                return RabbitConfig.ROUTING_TIMEOUT_ORDER_FINAL_DELAY;
            case TimeoutMessage.POOL_START:
                // 组局开始时间超时消息进入开局延迟队列。
                return RabbitConfig.ROUTING_TIMEOUT_POOL_START_DELAY;
            case TimeoutMessage.COMPLETED_CONFIRM:
            case TimeoutMessage.FINISHED_CONFIRM:
            default:
                // 成团确认、结束确认以及未知类型统一进入确认延迟队列。
                return RabbitConfig.ROUTING_TIMEOUT_CONFIRM_DELAY;
        }
    }
}
