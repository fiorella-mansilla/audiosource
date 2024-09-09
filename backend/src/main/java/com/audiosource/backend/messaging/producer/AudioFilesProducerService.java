package com.audiosource.backend.messaging.producer;

import com.audiosource.backend.dto.AudioFileMessage;
import com.audiosource.backend.messaging.config.AudioRabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AudioFilesProducerService {
    private final RabbitTemplate rabbitTemplate;
    private static final String EXCHANGE_NAME = AudioRabbitMQConfig.AUDIO_EXCHANGE_NAME;
    private static final String ROUTING_KEY = AudioRabbitMQConfig.AUDIO_ROUTING_KEY;

    @Autowired
    public AudioFilesProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishClientUploadNotification(AudioFileMessage audioFileMessage) {
        rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, audioFileMessage);
    }
}
