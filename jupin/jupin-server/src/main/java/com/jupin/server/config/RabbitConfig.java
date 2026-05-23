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
}
