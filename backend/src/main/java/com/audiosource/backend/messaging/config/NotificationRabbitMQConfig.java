package com.audiosource.backend.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationRabbitMQConfig {

    @Value("${notification.queue.name}")
    private String notificationQueueName;

    @Value("${notification.exchange.name}")
    private String notificationExchangeName;

    @Value("${notification.routing.key}")
    private String notificationRoutingKey;


    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(notificationExchangeName);
    }

    @Bean
    public Queue notificationQueue() {
        return new Queue(notificationQueueName, true);
    }

    @Bean
    public Binding notificationBinding(Queue notificationQueue, DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationQueue).to(notificationExchange).with(notificationRoutingKey);
    }
}
