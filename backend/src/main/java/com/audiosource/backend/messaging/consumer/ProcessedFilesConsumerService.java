package com.audiosource.backend.messaging.consumer;

import com.audiosource.backend.dto.NotificationMessage;
import com.audiosource.backend.dto.ProcessedFileMessage;
import com.audiosource.backend.exception.S3UploadException;
import com.audiosource.backend.messaging.producer.NotificationProducerService;
import com.audiosource.backend.service.metadata.FileMetadataService;
import com.audiosource.backend.service.s3.S3UploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ProcessedFilesConsumerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessedFilesConsumerService.class);

    private final S3UploadService s3UploadService;
    private final NotificationProducerService notificationProducerService;
    private final FileMetadataService fileMetadataService;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Autowired
    public ProcessedFilesConsumerService(S3UploadService s3UploadService, NotificationProducerService notificationProducerService, FileMetadataService fileMetadataService) {
        this.s3UploadService = s3UploadService;
        this.notificationProducerService = notificationProducerService;
        this.fileMetadataService = fileMetadataService;
    }

    @RabbitListener(queues = "${processedFiles.queue.name}")
    public void consumeProcessedFileMessage(ProcessedFileMessage processedFileMessage) {
        LOGGER.info("Received message from ProcessedFilesQueue: {}", processedFileMessage);

        String correlationId = processedFileMessage.getCorrelationId();
        String processedAudioFilePath = processedFileMessage.getProcessedAudioFilePath();

        if (processedAudioFilePath == null || processedAudioFilePath.isEmpty()) {
            LOGGER.error("No processedAudioFilePath found for correlationId: {}", correlationId);
            return;
        }

        try {
            // Upload the processed audio file to S3
            String preSignedUrl = s3UploadService.uploadDirectoryAsZipToS3(processedAudioFilePath, bucketName);

            if (preSignedUrl != null) {
                LOGGER.info("Processed file uploaded successfully to S3 for correlationId: {}", correlationId);

                String downloadUrl = preSignedUrl;

                // Save the downloadUrl and update the given fileMetadata collection in MongoDB
                boolean isUpdated = fileMetadataService.updateDownloadUrl(correlationId, downloadUrl);

                if (isUpdated) {
                    LOGGER.info("FileMetadata Collection updated successfully with downloadUrl for correlationId: {}", correlationId);

                    // Publish the notification message to the NotificationQueue
                    NotificationMessage notificationMessage = new NotificationMessage(correlationId, downloadUrl);
                    notificationProducerService.publishUploadToS3Notification(notificationMessage);
                } else {
                    LOGGER.warn("FileMetadata not found for correlationId: {}. Could not update downloadUrl.", correlationId);
                }
            } else {
                LOGGER.error("Failed to get pre-signed URL after upload for correlationId: {}", processedFileMessage.getCorrelationId());
                throw new S3UploadException("Pre-signed URL is null after upload.");
            }
        } catch (S3UploadException e) {
            LOGGER.error("Error uploading file for correlationId {}: {}", processedFileMessage.getCorrelationId(), e.getMessage());
        }
    }
}
