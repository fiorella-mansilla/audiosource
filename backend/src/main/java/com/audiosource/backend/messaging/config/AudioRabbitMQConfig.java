package com.audiosource.backend.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AudioRabbitMQConfig {

    // Constants for exchange and routing key
    public static final String AUDIO_EXCHANGE_NAME = "audioFilesExchange";
    public static final String AUDIO_ROUTING_KEY = "audio.routing.key";

    // Queue name
    public static final String AUDIO_FILES_QUEUE = "audioFilesQueue";

    @Bean
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("user");
        factory.setPassword("password");
        return factory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }

    // Define Exchange
    @Bean
    public DirectExchange audioExchange() {
        return new DirectExchange(AUDIO_EXCHANGE_NAME);
    }

    // Define Queue
    @Bean
    public Queue audioFilesQueue() {
        return new Queue(AUDIO_FILES_QUEUE, true);
    }

    // Define Binding
    @Bean
    public Binding audioFilesBinding(Queue audioFilesQueue, DirectExchange audioExchange) {
        return BindingBuilder.bind(audioFilesQueue).to(audioExchange).with(AUDIO_ROUTING_KEY);
    }
}
