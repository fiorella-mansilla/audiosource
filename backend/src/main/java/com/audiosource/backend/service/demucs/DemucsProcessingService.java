package com.audiosource.backend.service.demucs;

import com.audiosource.backend.exception.DemucsProcessingException;
import com.audiosource.backend.util.S3Utils;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class DemucsProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(DemucsProcessingService.class);
    private final Dotenv dotenv;
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList(".mp3", ".wav");

    public DemucsProcessingService(Dotenv dotenv) {
        this.dotenv = dotenv;
    }

    /**
     * Processes the downloaded audio file using Demucs for music source separation.
     *
     * @param originalAudioFilePath The absolute path of the audio file to process.
     * @throws DemucsProcessingException If an I/O error occurs or the process fails.
     */
    public void processDownloadedAudioFile(String originalAudioFilePath) throws DemucsProcessingException {
        try {
            File originalAudioFile = new File(originalAudioFilePath);

            // Validate if audio file exists
            if(!originalAudioFile.exists()) {
                throw new IllegalArgumentException("Audio file not found : " + originalAudioFilePath);
            }

            if (!isSupportedFormat(originalAudioFilePath)) {
                throw new IllegalArgumentException("Unsupported file format: " + originalAudioFilePath);
            }

            String pythonEnvPath = dotenv.get("PYTHON_ENV_PATH");
            String[] commandArgs = { pythonEnvPath, "-m", "demucs", "-d", "cpu", originalAudioFilePath };

            // Execute Demucs command via ProcessBuilder
            ProcessBuilder processBuilder = new ProcessBuilder(commandArgs);
            processBuilder.directory(new File(dotenv.get("DEMUCS_OUTPUT_DIRECTORY")));
            processBuilder.inheritIO();

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            // Handle processing errors
            if(exitCode != 0) {
                throw new IOException("Demucs processing failed for file: " + originalAudioFilePath);
            }
            logger.info("Successfully processed audio file {}", originalAudioFilePath);
        } catch (IOException | InterruptedException e) {
            throw new DemucsProcessingException("Error processing file " + originalAudioFilePath, e);
        }
    }

    /* Check if the file format from the downloaded file is supported by Demucs */
    private boolean isSupportedFormat(String originalAudioFilePath) {
        return SUPPORTED_FORMATS.stream().anyMatch(originalAudioFilePath::endsWith);
    }
}
