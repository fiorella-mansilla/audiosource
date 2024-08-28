package com.audiosource.backend.config.rabbitmq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessingRabbitMQConfig {

    // Constants for exchange and routing key
    public static final String PROCESSING_EXCHANGE_NAME = "processingExchange";
    public static final String PROCESSED_ROUTING_KEY = "processed.routing.key";
    public static final String ERROR_ROUTING_KEY = "error.routing.key";

    // Queue Names
    public static final String PROCESSED_FILES_QUEUE = "processedFilesQueue";
    public static final String ERROR_QUEUE = "errorQueue";

    @Bean
    public DirectExchange processingExchange() {
        return new DirectExchange(PROCESSING_EXCHANGE_NAME);
    }

    @Bean
    public Queue processedFilesQueue() {
        return new Queue(PROCESSED_FILES_QUEUE, true);
    }

    @Bean
    public Queue errorQueue() {
        return new Queue(ERROR_QUEUE, true);
    }

    @Bean
    public Binding processedFilesBinding(Queue processedFilesQueue, DirectExchange processingExchange) {
        return BindingBuilder.bind(processedFilesQueue).to(processingExchange).with(PROCESSED_ROUTING_KEY);
    }

    @Bean
    public Binding errorBinding(Queue errorQueue, DirectExchange processingExchange) {
        return BindingBuilder.bind(errorQueue).to(processingExchange).with(ERROR_ROUTING_KEY);
    }
}
