package com.audiosource.backend.service.demucs;

import com.audiosource.backend.enums.OutputFormat;
import com.audiosource.backend.enums.SeparationType;
import com.audiosource.backend.exception.DemucsProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Service
public class DemucsProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(DemucsProcessingService.class);
    private static final List<String> SUPPORTED_FORMATS = Arrays.asList(".mp3", ".wav");

    @Value("${demucs.outputDirectory}")
    private String demucsOutputDirectory;

    @Value("${python.env.path}")
    private String pythonEnvPath;

    /**
     * Processes the downloaded audio file using Demucs for music source separation.
     *
     * @param originalAudioFilePath The absolute path of the audio file to process.
     * @param separationType The type of separation to perform (vocal remover or stems splitter).
     * @param outputFormat The format of the output audio files (mp3 or wav).
     * @throws DemucsProcessingException If an I/O error occurs or the process fails.
     */
    public void processRetrievedAudioFile(String originalAudioFilePath, SeparationType separationType, OutputFormat outputFormat) throws DemucsProcessingException {
        // Ensure the service is ready for processing
        if (!isReadyForProcessing()) {
            throw new DemucsProcessingException("Service is not ready for processing. Check environment and output directory.");
        }
        try {
            File originalAudioFile = new File(originalAudioFilePath);

            // Validate if audio file exists
            if(!originalAudioFile.exists()) {
                throw new IllegalArgumentException("Audio file not found : " + originalAudioFilePath);
            }

            // Validate if audio format from original file is supported
            if (!isSupportedFormat(originalAudioFilePath)) {
                throw new IllegalArgumentException("Unsupported file format: " + originalAudioFilePath);
            }

            String[] commandArgs = { pythonEnvPath, "-m", "demucs", "-d", "cpu", originalAudioFilePath };

            // Execute Demucs command via ProcessBuilder
            ProcessBuilder processBuilder = new ProcessBuilder(commandArgs);
            processBuilder.directory(new File(demucsOutputDirectory));
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

    /**
     * Checks if the Demucs processing service is ready to handle audio processing.
     * This involves checking necessary conditions like environment variables, directories, etc.
     *
     * @return boolean indicating if the service is ready.
     */
    public boolean isReadyForProcessing() {
        // Check if the Python environment for Demucs exists and is executable
        File pythonEnv = new File(pythonEnvPath);
        if (!pythonEnv.exists() || !pythonEnv.canExecute()) {
            logger.error("Python environment for Demucs is not available or executable.");
            return false;
        }

        // Check if the Demucs output directory is available and writable
        File outputDir = new File(demucsOutputDirectory);
        if (!outputDir.exists() || !outputDir.canWrite()) {
            logger.error("Demucs output directory is not available or writable.");
            return false;
        }

        // If both checks pass, the service is ready for processing
        return true;
    }

    /* Check if the file format from the downloaded file is supported by Demucs */
    private boolean isSupportedFormat(String originalAudioFilePath) {
        return SUPPORTED_FORMATS.stream().anyMatch(originalAudioFilePath::endsWith);
    }
}
