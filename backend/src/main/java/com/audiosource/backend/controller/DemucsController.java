package com.audiosource.backend.controller;

import com.audiosource.backend.service.DemucsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

@RestController
@RequestMapping("/demucs")
public class DemucsController {

    private final DemucsService demucsService;

    @Autowired
    public DemucsController(DemucsService demucsService) {
        this.demucsService = demucsService;
    }

    @PostMapping("/process-file")
    public ResponseEntity<String> processNextAudioFile() {

        Optional<String> audioFilePathOpt = demucsService.retrieveNextAudioFilePath();

        if (audioFilePathOpt.isPresent()) {

            String audioFilePath = audioFilePathOpt.get();

            try {
                demucsService.processNextAudioFile(audioFilePath);
                // Delete the file from the input directory after processing it with Demucs
                File processedFile = new File(audioFilePath);
                if(processedFile.delete()) {
                    return ResponseEntity.status(200).body("File processed and deleted successfully: " + audioFilePath);
                } else {
                    return ResponseEntity.status(500).body("File processed but failed to delete: " + audioFilePath);
                }
            } catch (IOException | InterruptedException exc) {
                return ResponseEntity.status(500).body("Failed to process file: " + audioFilePath);
            }
        } else {
            return ResponseEntity.status(404).body("No files to process.");
        }
    }
}
