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
        // 超时消息先发送到延迟交换机。
        // 该交换机负责把不同类型的超时消息路由到各自的延迟队列。
        return new DirectExchange(EXCHANGE_TIMEOUT_DELAY);
    }

    @Bean
    public DirectExchange timeoutDlxExchange() {
        // 死信交换机。
        // 延迟队列里的消息 TTL 到期后，会被 RabbitMQ 投递到这个交换机。
        return new DirectExchange(EXCHANGE_TIMEOUT_DLX);
    }

    @Bean
    public Queue timeoutOrderDepositDelayQueue() {
        // 押金支付超时延迟队列。
        // 消息在这里等待到 TTL 到期，之后进入统一超时消费队列。
        return timeoutDelayQueue(QUEUE_TIMEOUT_ORDER_DEPOSIT_DELAY);
    }

    @Bean
    public Queue timeoutOrderFinalDelayQueue() {
        // 尾款支付超时延迟队列。
        // 与押金分队列存放，便于不同业务设置不同 TTL 和路由。
        return timeoutDelayQueue(QUEUE_TIMEOUT_ORDER_FINAL_DELAY);
    }

    @Bean
    public Queue timeoutPoolStartDelayQueue() {
        // 组局开始时间超时延迟队列。
        // 用于到开局时间仍无人加入时自动取消组局。
        return timeoutDelayQueue(QUEUE_TIMEOUT_POOL_START_DELAY);
    }

    @Bean
    public Queue timeoutConfirmDelayQueue() {
        // 成团确认和结束确认的延迟队列。
        // 确认窗口到期后由消费者做兜底判断。
        return timeoutDelayQueue(QUEUE_TIMEOUT_CONFIRM_DELAY);
    }

    private Queue timeoutDelayQueue(String queueName) {
        // 创建一个持久化延迟队列。
        // 队列本身不直接被业务消费者监听，只负责暂存带 TTL 的消息。
        return QueueBuilder.durable(queueName)
                // 指定消息过期后的死信交换机。
                // TTL 到期时，RabbitMQ 会把消息转发到 EXCHANGE_TIMEOUT_DLX。
                .deadLetterExchange(EXCHANGE_TIMEOUT_DLX)
                // 指定死信路由键。
                // 所有超时消息到期后统一路由到 timeout.queue。
                .deadLetterRoutingKey(ROUTING_TIMEOUT)
                .build();
    }

    @Bean
    public Queue timeoutQueue() {
        // 统一超时消费队列。
        // 消费者只监听这个队列，业务处理逻辑集中在 TimeoutConsumer。
        return QueueBuilder.durable(QUEUE_TIMEOUT).build();
    }

    @Bean
    public Binding timeoutOrderDepositDelayBinding() {
        // 把押金超时路由键绑定到押金延迟队列。
        return BindingBuilder.bind(timeoutOrderDepositDelayQueue())
                .to(timeoutDelayExchange())
                .with(ROUTING_TIMEOUT_ORDER_DEPOSIT_DELAY);
    }

    @Bean
    public Binding timeoutOrderFinalDelayBinding() {
        // 把尾款超时路由键绑定到尾款延迟队列。
        return BindingBuilder.bind(timeoutOrderFinalDelayQueue())
                .to(timeoutDelayExchange())
                .with(ROUTING_TIMEOUT_ORDER_FINAL_DELAY);
    }

    @Bean
    public Binding timeoutPoolStartDelayBinding() {
        // 把组局开始超时路由键绑定到组局开始延迟队列。
        return BindingBuilder.bind(timeoutPoolStartDelayQueue())
                .to(timeoutDelayExchange())
                .with(ROUTING_TIMEOUT_POOL_START_DELAY);
    }

    @Bean
    public Binding timeoutConfirmDelayBinding() {
        // 把确认超时路由键绑定到确认延迟队列。
        return BindingBuilder.bind(timeoutConfirmDelayQueue())
                .to(timeoutDelayExchange())
                .with(ROUTING_TIMEOUT_CONFIRM_DELAY);
    }

    @Bean
    public Binding timeoutBinding() {
        // 把死信交换机和统一超时队列绑定起来。
        // 各延迟队列中过期的消息最终都会进入 timeout.queue。
        return BindingBuilder.bind(timeoutQueue()).to(timeoutDlxExchange()).with(ROUTING_TIMEOUT);
    }
}
