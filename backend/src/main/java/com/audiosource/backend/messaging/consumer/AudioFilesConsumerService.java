package com.audiosource.backend.messaging.consumer;

import com.audiosource.backend.dto.AudioFileMessage;
import com.audiosource.backend.entities.FileMetadata;
import com.audiosource.backend.service.demucs.DemucsProcessingService;
import com.audiosource.backend.service.metadata.FileMetadataService;
import com.audiosource.backend.service.s3.S3DownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AudioFilesConsumerService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioFilesConsumerService.class);

    private final S3DownloadService s3DownloadService;
    private final FileMetadataService fileMetadataService;
    private final DemucsProcessingService demucsProcessingService;

    @Autowired
    public AudioFilesConsumerService(S3DownloadService s3DownloadService, FileMetadataService fileMetadataService, DemucsProcessingService demucsProcessingService) {
        this.s3DownloadService = s3DownloadService;
        this.fileMetadataService = fileMetadataService;
        this.demucsProcessingService = demucsProcessingService;
    }

    @RabbitListener(queues = "${audioFiles.queue.name}")
    public void consumeAudioFileMessage(AudioFileMessage message) {
        LOGGER.info("Received message: {}", message);

        // Retrieve file metadata using correlationId
        Optional<FileMetadata> fileMetadataOpt = fileMetadataService.findByCorrelationId(message.getCorrelationId());
        if (fileMetadataOpt.isEmpty()) {
            LOGGER.error("No file metadata found for correlationId: {}", message.getCorrelationId());
            return;
        }
//        FileMetadata fileMetadata = fileMetadataOpt.get();

        // Trigger download of latest audio file from S3 bucket
        Optional<String> downloadedFilePath = s3DownloadService.getObjectFromBucket(message);
        if (downloadedFilePath.isPresent()) {
            String originalAudioFilePath = downloadedFilePath.get();

            // Trigger processing of audio file using Demucs
            //TODO: After refactoring Demucs Service, pass message.getSeparationType() and message.getOutputFormat()
            demucsProcessingService.processNextAudioFile(originalAudioFilePath);

            LOGGER.info("File processing completed for correlation ID {}", message.getCorrelationId());

            //TODO: Update File metadata with processing status?
        } else {
            LOGGER.error("Failed to download file for correlation ID {}", message.getCorrelationId());
        }
    }
}
