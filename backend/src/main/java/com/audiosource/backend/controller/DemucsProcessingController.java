package com.audiosource.backend.controller;

import com.audiosource.backend.service.demucs.DemucsProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demucs")
public class DemucsProcessingController {
    private static final Logger LOGGER = LoggerFactory.getLogger(DemucsProcessingController.class);
    private final DemucsProcessingService demucsProcessingService;

    @Autowired
    public DemucsProcessingController(DemucsProcessingService demucsProcessingService) {
        this.demucsProcessingService = demucsProcessingService;
    }

    @GetMapping("/status")
    public ResponseEntity<String> checkServiceStatus() {
        try {
            // This checks if the DemucsProcessingService is ready
            if (demucsProcessingService.isReadyForProcessing()) {
                return ResponseEntity.ok("Service is available for processing");
            } else {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body("Service is not ready for processing");
            }
        } catch (Exception e) {
            LOGGER.error("Error checking service status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("An error occurred while checking the service status");
        }
    }
}
