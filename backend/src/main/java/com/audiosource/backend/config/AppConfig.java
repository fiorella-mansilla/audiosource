package com.audiosource.backend.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public Dotenv dotenv() {
        // Load the .env file
        Dotenv dotenv = Dotenv.configure().load();

        // Set system properties so SpringBoot can access them in the application.properties file
        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });

        return dotenv;
    }
}
