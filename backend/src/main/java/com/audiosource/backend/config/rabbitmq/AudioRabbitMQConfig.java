package com.audiosource.backend.config.rabbitmq;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AudioRabbitMQConfig {

    // Constants for exchange and routing key
    public static final String AUDIO_EXCHANGE_NAME = "audioFilesExchange";
    public static final String AUDIO_ROUTING_KEY = "audio.routing.key";

    // Queue name
    public static final String AUDIO_FILES_QUEUE = "audioFilesQueue";

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
