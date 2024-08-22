package com.audiosource.backend.controller;

import com.audiosource.backend.exception.DemucsProcessingException;
import com.audiosource.backend.service.demucs.DemucsProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.Optional;

@RestController
@RequestMapping("/demucs")
public class DemucsProcessingController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemucsProcessingController.class);
    private final DemucsProcessingService demucsProcessingService;

    @Autowired
    public DemucsProcessingController(DemucsProcessingService demucsProcessingService) {
        this.demucsProcessingService = demucsProcessingService;
    }

    /**
     * Processes the next available audio file in the Queue, triggers Demucs and deletes the processed file.
     *
     * @return ResponseEntity with the result of the file processing and deletion.
     */
    @PostMapping("/process-file")
    public ResponseEntity<String> processNextAudioFile() {

        // Retrieve the next audio file path to process
        Optional<String> audioFilePathOpt = demucsProcessingService.retrieveNextAudioFilePath();

        if (audioFilePathOpt.isPresent()) {
            String audioFilePath = audioFilePathOpt.get();
            try {
                // Process the audio file
                demucsProcessingService.processNextAudioFile(audioFilePath);

                // Delete the file from the input directory after processing it with Demucs
                File processedFile = new File(audioFilePath);

                if(processedFile.delete()) {
                    LOGGER.info("File processed and deleted successfully: {}", audioFilePath);
                    return ResponseEntity.status(HttpStatus.OK).body("File processed and deleted successfully.");
                } else {
                    LOGGER.warn("File processed but failed to delete: {}", audioFilePath);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("File processed but failed to delete.");
                }
            } catch (DemucsProcessingException e) {
                LOGGER.error("Demucs processing error for file {}: {}", audioFilePath, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to process the file : " + e.getMessage());
            } catch (Exception e) {
                LOGGER.error("Unexpected error processing file {}: {}", audioFilePath, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("An unexpected error occurred while processing the file : " + e.getMessage());
            }
        } else {
            LOGGER.info("No files to process.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No files to process.");
        }
    }
}
