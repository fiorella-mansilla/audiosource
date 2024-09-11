package com.audiosource.backend.messaging.producer;

import com.audiosource.backend.dto.ErrorProcessingMessage;
import com.audiosource.backend.dto.ProcessedFileMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProcessedFilesProducerService {
    private final RabbitTemplate rabbitTemplate;
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessedFilesProducerService.class);

    @Value("${processedFiles.exchange.name}")
    private String processedFilesExchangeName;

    @Value("${processedFiles.routing.key}")
    private String processedFilesRoutingKey;

    @Value("${error.queue.name}")
    private String errorQueueName;

    @Value("${error.routing.key}")
    private String errorRoutingKey;

    @Autowired
    public ProcessedFilesProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    // Publish success message to RabbitMQ
    public void publishProcessedFileNotification(ProcessedFileMessage processedFileMessage) {
        rabbitTemplate.convertAndSend(processedFilesExchangeName, processedFilesRoutingKey, processedFileMessage);
        LOGGER.info("Published message to ProcessedFilesQueue: {}", processedFileMessage);
    }

    // Publish error message to RabbitMQ
    public void publishErrorProcessingNotification(ErrorProcessingMessage errorProcessingMessage) {
        rabbitTemplate.convertAndSend(processedFilesExchangeName, errorRoutingKey, errorProcessingMessage);
        LOGGER.error("Published error message to ErrorQueue: {}", errorProcessingMessage);
    }
}
