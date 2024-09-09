package com.audiosource.backend.controller;

import com.audiosource.backend.service.demucs.DemucsProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
}
