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
        long safeDelay = Math.max(delayMillis, 1000L);
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE_TIMEOUT_DELAY,
                resolveRoutingKey(message),
                JSONUtil.toJsonStr(message),
                msg -> {
                    msg.getMessageProperties().setExpiration(String.valueOf(safeDelay));
                    return msg;
                });
    }

    private String resolveRoutingKey(TimeoutMessage message) {
        if (message == null || message.getType() == null) {
            return RabbitConfig.ROUTING_TIMEOUT_CONFIRM_DELAY;
        }
        switch (message.getType()) {
            case TimeoutMessage.ORDER_DEPOSIT_PAYMENT:
            case TimeoutMessage.ORDER_PAYMENT:
                return RabbitConfig.ROUTING_TIMEOUT_ORDER_DEPOSIT_DELAY;
            case TimeoutMessage.ORDER_FINAL_PAYMENT:
                return RabbitConfig.ROUTING_TIMEOUT_ORDER_FINAL_DELAY;
            case TimeoutMessage.POOL_START:
                return RabbitConfig.ROUTING_TIMEOUT_POOL_START_DELAY;
            case TimeoutMessage.COMPLETED_CONFIRM:
            case TimeoutMessage.FINISHED_CONFIRM:
            default:
                return RabbitConfig.ROUTING_TIMEOUT_CONFIRM_DELAY;
        }
    }
}
