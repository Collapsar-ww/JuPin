package com.jupin.server.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_MATCH = "match.exchange";
    public static final String QUEUE_MATCH = "match.queue";
    public static final String ROUTING_MATCH = "match.routing";

    public static final String EXCHANGE_NOTIFICATION = "notification.exchange";
    public static final String QUEUE_NOTIFICATION = "notification.queue";
    public static final String ROUTING_NOTIFICATION = "notification.routing";

    public static final String EXCHANGE_TIMEOUT_DELAY = "timeout.delay.exchange";
    public static final String QUEUE_TIMEOUT_ORDER_DEPOSIT_DELAY = "timeout.order.deposit.delay.queue";
    public static final String QUEUE_TIMEOUT_ORDER_FINAL_DELAY = "timeout.order.final.delay.queue";
    public static final String QUEUE_TIMEOUT_POOL_START_DELAY = "timeout.pool.start.delay.queue";
    public static final String QUEUE_TIMEOUT_CONFIRM_DELAY = "timeout.confirm.delay.queue";
    public static final String ROUTING_TIMEOUT_ORDER_DEPOSIT_DELAY = "timeout.order.deposit.delay.routing";
    public static final String ROUTING_TIMEOUT_ORDER_FINAL_DELAY = "timeout.order.final.delay.routing";
    public static final String ROUTING_TIMEOUT_POOL_START_DELAY = "timeout.pool.start.delay.routing";
    public static final String ROUTING_TIMEOUT_CONFIRM_DELAY = "timeout.confirm.delay.routing";
    public static final String EXCHANGE_TIMEOUT_DLX = "timeout.dlx.exchange";
    public static final String QUEUE_TIMEOUT = "timeout.queue";
    public static final String ROUTING_TIMEOUT = "timeout.routing";

    @Bean
    public DirectExchange matchExchange() {
        return new DirectExchange(EXCHANGE_MATCH);
    }

    @Bean
    public Queue matchQueue() {
        return QueueBuilder.durable(QUEUE_MATCH).build();
    }

    @Bean
    public Binding matchBinding() {
        return BindingBuilder.bind(matchQueue()).to(matchExchange()).with(ROUTING_MATCH);
    }

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(EXCHANGE_NOTIFICATION);
    }

    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATION).build();
    }

    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue()).to(notificationExchange()).with(ROUTING_NOTIFICATION);
    }

    @Bean
    public DirectExchange timeoutDelayExchange() {
        return new DirectExchange(EXCHANGE_TIMEOUT_DELAY);
    }

    @Bean
    public DirectExchange timeoutDlxExchange() {
        return new DirectExchange(EXCHANGE_TIMEOUT_DLX);
    }

    @Bean
    public Queue timeoutOrderDepositDelayQueue() {
        return timeoutDelayQueue(QUEUE_TIMEOUT_ORDER_DEPOSIT_DELAY);
    }

    @Bean
    public Queue timeoutOrderFinalDelayQueue() {
        return timeoutDelayQueue(QUEUE_TIMEOUT_ORDER_FINAL_DELAY);
    }

    @Bean
    public Queue timeoutPoolStartDelayQueue() {
        return timeoutDelayQueue(QUEUE_TIMEOUT_POOL_START_DELAY);
    }

    @Bean
    public Queue timeoutConfirmDelayQueue() {
        return timeoutDelayQueue(QUEUE_TIMEOUT_CONFIRM_DELAY);
    }

    private Queue timeoutDelayQueue(String queueName) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(EXCHANGE_TIMEOUT_DLX)
                .deadLetterRoutingKey(ROUTING_TIMEOUT)
                .build();
    }

    @Bean
    public Queue timeoutQueue() {
        return QueueBuilder.durable(QUEUE_TIMEOUT).build();
    }

    @Bean
    public Binding timeoutOrderDepositDelayBinding() {
        return BindingBuilder.bind(timeoutOrderDepositDelayQueue())
                .to(timeoutDelayExchange())
                .with(ROUTING_TIMEOUT_ORDER_DEPOSIT_DELAY);
    }

    @Bean
    public Binding timeoutOrderFinalDelayBinding() {
        return BindingBuilder.bind(timeoutOrderFinalDelayQueue())
                .to(timeoutDelayExchange())
                .with(ROUTING_TIMEOUT_ORDER_FINAL_DELAY);
    }

    @Bean
    public Binding timeoutPoolStartDelayBinding() {
        return BindingBuilder.bind(timeoutPoolStartDelayQueue())
                .to(timeoutDelayExchange())
                .with(ROUTING_TIMEOUT_POOL_START_DELAY);
    }

    @Bean
    public Binding timeoutConfirmDelayBinding() {
        return BindingBuilder.bind(timeoutConfirmDelayQueue())
                .to(timeoutDelayExchange())
                .with(ROUTING_TIMEOUT_CONFIRM_DELAY);
    }

    @Bean
    public Binding timeoutBinding() {
        return BindingBuilder.bind(timeoutQueue()).to(timeoutDlxExchange()).with(ROUTING_TIMEOUT);
    }
}
