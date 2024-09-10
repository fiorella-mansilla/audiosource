package com.audiosource.backend.messaging.producer;

import com.audiosource.backend.dto.AudioFileMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AudioFilesProducerService {
    private final RabbitTemplate rabbitTemplate;
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioFilesProducerService.class);

    @Value("${audioFiles.exchange.name}")
    private String audioFilesExchangeName;

    @Value("${audioFiles.routing.key}")
    private String audioFilesRoutingKey;

    @Autowired
    public AudioFilesProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishClientUploadNotification(AudioFileMessage audioFileMessage) {
        rabbitTemplate.convertAndSend(audioFilesExchangeName, audioFilesRoutingKey, audioFileMessage);
        LOGGER.info("Published message to AudioFilesQueue: {}", audioFileMessage);
    }
}
