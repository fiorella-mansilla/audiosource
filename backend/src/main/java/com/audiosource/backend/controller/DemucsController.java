package com.audiosource.backend.controller;

import com.audiosource.backend.exception.DemucsProcessingException;
import com.audiosource.backend.service.demucs.DemucsService;
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
public class DemucsController {

    private static final Logger logger = LoggerFactory.getLogger(DemucsController.class);
    private final DemucsService demucsService;

    @Autowired
    public DemucsController(DemucsService demucsService) {
        this.demucsService = demucsService;
    }

    /**
     * Processes the next available audio file in the Queue, triggers Demucs and deletes the processed file.
     *
     * @return ResponseEntity with the result of the file processing and deletion.
     */
    @PostMapping("/process-file")
    public ResponseEntity<String> processNextAudioFile() {

        // Retrieve the next audio file path to process
        Optional<String> audioFilePathOpt = demucsService.retrieveNextAudioFilePath();

        if (audioFilePathOpt.isPresent()) {
            String audioFilePath = audioFilePathOpt.get();
            try {
                // Process the audio file
                demucsService.processNextAudioFile(audioFilePath);

                // Delete the file from the input directory after processing it with Demucs
                File processedFile = new File(audioFilePath);

                if(processedFile.delete()) {
                    logger.info("File processed and deleted successfully: {}", audioFilePath);
                    return ResponseEntity.status(HttpStatus.OK).body("File processed and deleted successfully.");
                } else {
                    logger.warn("File processed but failed to delete: {}", audioFilePath);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("File processed but failed to delete.");
                }
            } catch (DemucsProcessingException e) {
                logger.error("Demucs processing error for file {}: {}", audioFilePath, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Failed to process the file : " + e.getMessage());
            } catch (Exception e) {
                logger.error("Unexpected error processing file {}: {}", audioFilePath, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("An unexpected error occurred while processing the file : " + e.getMessage());
            }
        } else {
            logger.info("No files to process.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No files to process.");
        }
    }
}
