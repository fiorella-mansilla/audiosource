package com.audiosource.backend.messaging.producer;

import com.audiosource.backend.dto.NotificationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationProducerService {
    private final RabbitTemplate rabbitTemplate;
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationProducerService.class);

    @Value("${notification.exchange.name}")
    private String notificationExchangeName;

    @Value("${notification.routing.key}")
    private String notificationRoutingKey;

    @Autowired
    public NotificationProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // Publish Notification message to RabbitMQ
    public void publishUploadToS3Notification(NotificationMessage notificationMessage) {
        rabbitTemplate.convertAndSend(notificationExchangeName, notificationRoutingKey, notificationMessage);
        LOGGER.info("Published message to NotificationQueue: {}", notificationMessage);
    }
}
