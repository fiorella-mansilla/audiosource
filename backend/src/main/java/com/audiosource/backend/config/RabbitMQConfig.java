package com.audiosource.backend.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue audioSourceQueue() {
        return new Queue("audiosource-queue", true);
    }
}
