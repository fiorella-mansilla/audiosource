package com.audiosource.backend.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProcessingRabbitMQConfig {

    @Value("${spring.rabbitmq.host}")
    private String rabbitmqHost;

    @Value("${spring.rabbitmq.port}")
    private int rabbitmqPort;

    @Value("${spring.rabbitmq.username}")
    private String rabbitmqUsername;

    @Value("${spring.rabbitmq.password}")
    private String rabbitmqPassword;

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

    @Bean
    public Queue processedFilesQueue() {
        return new Queue(processedFilesQueueName, true);
    }

    @Bean
    public Binding processedFilesBinding(Queue processedFilesQueue, DirectExchange processingExchange) {
        return BindingBuilder.bind(processedFilesQueue).to(processingExchange).with(processedFilesRoutingKey);
    }

    @Bean
    public Queue errorQueue() {
        return new Queue(errorQueueName, true);
    }

    @Bean
    public Binding errorBinding(Queue errorQueue, DirectExchange processingExchange) {
        return BindingBuilder.bind(errorQueue).to(processingExchange).with(errorRoutingKey);
    }

    /* ConnectionFactory bean to establish a connection to RabbitMQ */
    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost(rabbitmqHost);
        factory.setPort(rabbitmqPort);
        factory.setUsername(rabbitmqUsername);
        factory.setPassword(rabbitmqPassword);
        return factory;
    }

    /* Message Serialization for producers : Ensures that outgoing messages are automatically converted
from Java objects to JSON when sent to RabbitMQ.*/
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }
}
