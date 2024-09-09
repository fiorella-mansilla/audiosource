package com.audiosource.backend.messaging.consumer;

import com.audiosource.backend.dto.AudioFileMessage;
import com.audiosource.backend.entities.FileMetadata;
import com.audiosource.backend.exception.DemucsProcessingException;
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

    /* Consumes the AudioFileMessage from RabbitMQ (`audioFilesQueue`) and initiates the audio processing workflow which:
     * 1. Downloads the audio file from S3 bucket
     * 2. Processes the audio file using DEMUCS
     * @param message: AudioFileMessage containing all the necessary metadata for
     * downloading and processing the audio file
    * */
    @RabbitListener(queues = "${audioFiles.queue.name}")
    public void consumeAudioFileMessage(AudioFileMessage message) {
        LOGGER.info("Received message: {}", message);

        // Retrieves metadata for the audio file using FileMetadataService
        Optional<FileMetadata> fileMetadataOpt = fileMetadataService.findByCorrelationId(message.getCorrelationId());
        if (fileMetadataOpt.isEmpty()) {
            LOGGER.error("No file metadata found for correlationId: {}", message.getCorrelationId());
            return;
        }
//        FileMetadata fileMetadata = fileMetadataOpt.get();

        // Downloads the audio file from S3 bucket using S3DownloadService
        Optional<String> downloadedFilePath = s3DownloadService.getObjectFromBucket(message);
        if (downloadedFilePath.isPresent()) {
            String originalAudioFilePath = downloadedFilePath.get();

            // Triggers the processing of the downloaded file using DemucsProcessingService
            try {
                // TODO: In future, pass message.getSeparationType() and message.getOutputFormat() from message
                demucsProcessingService.processNextAudioFile(originalAudioFilePath);

                LOGGER.info("File processing completed for correlation ID {}", message.getCorrelationId());

                // TODO: Update the file metadata with the processing status (e.g., "COMPLETED"). ?
                // fileMetadataService.updateStatus(fileMetadataOpt.get(), "COMPLETED");
            } catch (DemucsProcessingException e) {
                LOGGER.error("Failed to process file for correlation ID {}", message.getCorrelationId());
                // TODO: Optionally update metadata with failure status (errorQueue)
            }

        } else {
            LOGGER.error("Failed to download file for correlation ID {}", message.getCorrelationId());
            // TODO: Optionally update metadata with download failure status (errorQueue)
        }
    }
}
