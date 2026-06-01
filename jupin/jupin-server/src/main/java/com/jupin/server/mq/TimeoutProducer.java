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
                RabbitConfig.ROUTING_TIMEOUT_DELAY,
                JSONUtil.toJsonStr(message),
                msg -> {
                    msg.getMessageProperties().setExpiration(String.valueOf(safeDelay));
                    return msg;
                });
    }
}
