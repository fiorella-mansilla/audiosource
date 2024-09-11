package com.audiosource.backend.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AudioRabbitMQConfig {

    @Value("${audioFiles.queue.name}")
    private String audioFilesQueueName;

    @Value("${audioFiles.exchange.name}")
    private String audioFilesExchangeName;

    @Value("${audioFiles.routing.key}")
    private String audioFilesRoutingKey;

    // Define Exchange which ensures that messages are routed to the queue with a specific routing key
    @Bean
    public DirectExchange audioExchange() {
        return new DirectExchange(audioFilesExchangeName);
    }

    // Define Queue
    @Bean
    public Queue audioFilesQueue() {
        return new Queue(audioFilesQueueName, true);
    }

    // Define Binding
    @Bean
    public Binding audioFilesBinding(Queue audioFilesQueue, DirectExchange audioExchange) {
        return BindingBuilder.bind(audioFilesQueue).to(audioExchange).with(audioFilesRoutingKey);
    }
}
