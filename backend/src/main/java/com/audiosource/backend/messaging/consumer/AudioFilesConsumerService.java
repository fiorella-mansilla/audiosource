package com.audiosource.backend.messaging.consumer;

import com.audiosource.backend.dto.AudioFileMessage;
import com.audiosource.backend.dto.ErrorProcessingMessage;
import com.audiosource.backend.dto.ProcessedFileMessage;
import com.audiosource.backend.dto.ProcessingContext;
import com.audiosource.backend.entities.FileMetadata;
import com.audiosource.backend.exception.DemucsProcessingException;
import com.audiosource.backend.messaging.producer.ProcessedFilesProducerService;
import com.audiosource.backend.service.demucs.DemucsProcessingService;
import com.audiosource.backend.service.metadata.FileMetadataService;
import com.audiosource.backend.service.s3.S3DownloadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class AudioFilesConsumerService {
    private final S3DownloadService s3DownloadService;
    private final FileMetadataService fileMetadataService;
    private final DemucsProcessingService demucsProcessingService;
    private final ProcessedFilesProducerService processedFilesProducerService;
    private static final Logger LOGGER = LoggerFactory.getLogger(AudioFilesConsumerService.class);

    @Autowired
    public AudioFilesConsumerService(S3DownloadService s3DownloadService, FileMetadataService fileMetadataService, DemucsProcessingService demucsProcessingService, ProcessedFilesProducerService processedFilesProducerService) {
        this.s3DownloadService = s3DownloadService;
        this.fileMetadataService = fileMetadataService;
        this.demucsProcessingService = demucsProcessingService;
        this.processedFilesProducerService = processedFilesProducerService;
    }

    /* Consumes the AudioFileMessage from RabbitMQ (`audioFilesQueue`) and initiates the audio processing workflow which:
     * 1. Downloads the audio file from S3 bucket using S3DownloadService
     * 2. Processes the audio file using DemucsProcessingService
     * @param audioFileMessage: Message dto containing all the necessary metadata for downloading and processing the audio file
    * */
    @RabbitListener(queues = "${audioFiles.queue.name}")
    public void consumeAudioFileMessage(AudioFileMessage audioFileMessage) {
        LOGGER.info("Received message from AudioFilesQueue: {}", audioFileMessage);

        // Retrieves metadata for the audio file using FileMetadataService
        Optional<FileMetadata> fileMetadataOpt = fileMetadataService.findByCorrelationId(audioFileMessage.getCorrelationId());
        if (fileMetadataOpt.isEmpty()) {
            LOGGER.error("No file metadata found for correlationId: {}", audioFileMessage.getCorrelationId());
            return;
        }

        // Downloads the audio file from S3 bucket using S3DownloadService
        Optional<String> downloadedFilePath = s3DownloadService.getObjectFromBucket(audioFileMessage);
        if (downloadedFilePath.isPresent()) {
            String originalAudioFilePath = downloadedFilePath.get();
            /* After downloading, a new ProcessingContext object containing the metadata needed by Demucs is passed
            to the processFileAsync method, which runs asynchronously.*/
            ProcessingContext context = new ProcessingContext(originalAudioFilePath, audioFileMessage);
            processFileAsync(context);  // Asynchronous processing
        } else {
            LOGGER.error("Failed to download file for correlation ID {}", audioFileMessage.getCorrelationId());
        }
    }

    /* The DemucsProcessingService processes the file asynchronously using the path and metadata/
    * Using @Async offloads the file processing task from the main execution flow. This way, the
    * system can handle multiple files concurrently without blocking the consumer's main thread.  */
    @Async
    public void processFileAsync(ProcessingContext context) {
        String originalAudioFilePath = context.getOriginalAudioFilePath();
        AudioFileMessage audioFileMessage = context.getAudioFileMessage();

        try {
            LOGGER.info("Processing file for correlation ID {}", audioFileMessage.getCorrelationId());

            // Get the processed audio file path after successful processing
            String processedAudioFilePath = demucsProcessingService.processRetrievedAudioFile(
                    originalAudioFilePath,
                    audioFileMessage.getSeparationType(),
                    audioFileMessage.getOutputFormat());

            LOGGER.info("File processing completed for correlation ID {}", audioFileMessage.getCorrelationId());

            // Publish success message to ProcessedFilesQueue using ProcessedFilesProducerService
            ProcessedFileMessage processedFileMessage = new ProcessedFileMessage(
                    audioFileMessage.getCorrelationId(),
                    processedAudioFilePath);

            processedFilesProducerService.publishProcessedFileNotification(processedFileMessage);

        } catch (DemucsProcessingException e) {
            LOGGER.error("Failed to process file for correlation ID {}", audioFileMessage.getCorrelationId());

            // Build the error message DTO
            ErrorProcessingMessage errorProcessingMessage = new ErrorProcessingMessage(
                    audioFileMessage.getCorrelationId(),
                    e.getMessage(),
                    LocalDateTime.now(),
                    0,
                    context
            );
            processedFilesProducerService.publishErrorProcessingNotification(errorProcessingMessage);
        }
    }
}
