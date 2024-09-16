package com.audiosource.backend.messaging.consumer;

import com.audiosource.backend.dto.NotificationMessage;
import com.audiosource.backend.service.metadata.FileMetadataService;
import com.audiosource.backend.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class NotificationConsumerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationConsumerService.class);

    private final NotificationService notificationService;
    private final FileMetadataService fileMetadataService;

    @Autowired
    public NotificationConsumerService(NotificationService notificationService, FileMetadataService fileMetadataService) {
        this.notificationService = notificationService;
        this.fileMetadataService = fileMetadataService;
    }

    // Consumes the NotificationMessage from the NotificationQueue
    @RabbitListener(queues = "${notification.queue.name}")
    public void consumeNotificationMessage(NotificationMessage notificationMessage) {
        LOGGER.info("Received message from NotificationQueue: {}", notificationMessage);

        if (!isValidMessage(notificationMessage)) {
            LOGGER.error("Invalid NotificationMessage received: {}", notificationMessage);
            return;
        }

        // Retrieve the correlationId and downloadUrl from the NotificationMessage
        String correlationId = notificationMessage.getCorrelationId();
        String downloadUrl = notificationMessage.getDownloadUrl();

        try {
            // Retrieve the User email from the FileMetadata collection in MongoDB
            Optional<String> optionalUserEmail = fileMetadataService.findUserEmailByCorrelationId(correlationId);

            if (optionalUserEmail.isEmpty()) {
                LOGGER.error("No User email found for correlationId: {}", correlationId);
                return;
            }

            String userEmail = optionalUserEmail.get();

            // Send email to the User with the downloadUrl
            boolean isNotificationSent = notificationService.sendEmailToUser(userEmail, downloadUrl);

            if (isNotificationSent) {
                LOGGER.info("Email sent successfully to the User for correlationId: {}", correlationId);
                // Update the notification status in the given fileMetadata collection in MongoDB
                fileMetadataService.updateNotificationStatus(correlationId);
            } else {
                LOGGER.error("Failed to send email to the User for correlationId: {}", correlationId);
            }
        } catch (Exception e) {
            LOGGER.error("Error processing notification for correlationId {}: {}", correlationId, e.getMessage(), e);
        }
    }

    // Updates the Notification Status to 'SENT' in the FileMetadata collection in MongoDB
    private void updateNotificationStatus(String correlationId) {
        boolean isUpdated = fileMetadataService.updateNotificationStatus(correlationId);
        if (isUpdated) {
            LOGGER.info("FileMetadata collection updated successfully with Notification status for correlationId: {}", correlationId);
        } else {
            LOGGER.error("Failed to update FileMetadata collection for correlationId: {}", correlationId);
        }
    }

    // Checks if the NotificationMessage is valid
    private boolean isValidMessage(NotificationMessage notificationMessage) {
        return notificationMessage != null && notificationMessage.getDownloadUrl() != null && !notificationMessage.getDownloadUrl().isEmpty();
    }
}
