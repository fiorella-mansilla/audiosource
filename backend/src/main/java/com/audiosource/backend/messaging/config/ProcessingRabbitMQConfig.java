package com.audiosource.backend.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessingRabbitMQConfig {

    @Value("${processedFiles.queue.name}")
    private String processedFilesQueueName;

    @Value("${processedFiles.exchange.name}")
    private String processedFilesExchangeName;

    @Value("${processedFiles.routing.key}")
    private String processedFilesRoutingKey;

    @Value("${error.queue.name}")
    private String errorQueueName;

    @Value("${error.routing.key}")
    private String errorRoutingKey;

    @Bean
    public DirectExchange processingExchange() {
        return new DirectExchange(processedFilesExchangeName);
    }

    /* Enable a retry mechanism and a Dead-Letter queue (DLQ) handling.
    * Use RabbitMQ's Dead-Letter Exchange (DLX) feature for allowing any message that cannot be processed
    * successfully to be automatically routed to a dead-letter queue (DLQ). This is useful for tasks that
    * may fail multiple times due to transient errors (e.g., network errors, temporary system overload)
    * and need retries. */
    @Bean
    public Queue processedFilesQueue() {
        return QueueBuilder.durable(processedFilesQueueName)
                // Routes the message to the errorQueue after failing.
                .withArgument("x-dead-letter-exchange", processingExchange().getName())
                .withArgument("x-dead-letter-routing-key", errorRoutingKey)
                .withArgument("x-message-ttl", 60000)  //  Sets a time-to-live on the message in the queue before it’s retried.
                .build();
    }

    @Bean
    public Queue errorQueue() {
        return QueueBuilder.durable(errorQueueName).build();
    }

    @Bean
    public Binding processedFilesBinding(Queue processedFilesQueue, DirectExchange processingExchange) {
        return BindingBuilder.bind(processedFilesQueue).to(processingExchange).with(processedFilesRoutingKey);
    }

    @Bean
    public Binding errorBinding(Queue errorQueue, DirectExchange processingExchange) {
        return BindingBuilder.bind(errorQueue).to(processingExchange).with(errorRoutingKey);
    }
}
