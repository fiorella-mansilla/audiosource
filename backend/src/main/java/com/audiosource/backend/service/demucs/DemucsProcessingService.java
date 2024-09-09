package com.audiosource.backend.service.demucs;

import com.audiosource.backend.exception.DemucsProcessingException;
import com.audiosource.backend.util.S3Utils;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;

@Service
public class DemucsProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(DemucsProcessingService.class);
    private final Dotenv dotenv;

    /**
     * Constructor that initializes the service and populates the audioFilesQueue with files from the input directory.
     */
    public DemucsProcessingService(Dotenv dotenv) {
        this.dotenv = dotenv;
    }

    /**
     * Processes the specified audio file using Demucs for music source separation.
     *
     * @param originalAudioFilePath The absolute path of the audio file to process.
     * @throws DemucsProcessingException If an I/O error occurs, if the thread is interrupted, or
     *                                   if the file format is unsupported.
     */
    public void processNextAudioFile(String originalAudioFilePath) throws DemucsProcessingException {
        try {
            File audioFile = new File(originalAudioFilePath);

            // Validate if audio file exists
            if(!audioFile.exists()) {
                throw new IllegalArgumentException("Audio file not found : " + originalAudioFilePath);
            }

            String pythonEnvPath;

            // Determine Python environment path based on file extension
            if (S3Utils.isSupportedFormat(originalAudioFilePath)) {
                pythonEnvPath = dotenv.get("PYTHON_ENV_PATH");
                String[] commandArgs = { pythonEnvPath, "-m", "demucs", "-d", "cpu", originalAudioFilePath };

                // Execute Demucs command via ProcessBuilder
                ProcessBuilder processBuilder = new ProcessBuilder(commandArgs);
                processBuilder.directory(new File(dotenv.get("DEMUCS_OUTPUT_DIRECTORY")));
                processBuilder.inheritIO();

                Process process = processBuilder.start();
                int exitCode = process.waitFor();

                // Check Demucs process exit code
                if(exitCode != 0) {
                    throw new IOException("Demucs processing failed:" + originalAudioFilePath);
                }
                logger.info("Successfully processed audio file {}", originalAudioFilePath);
            } else {
                throw new IllegalArgumentException("Unsupported file format.");
            }
        } catch (IOException | InterruptedException e) {
            throw new DemucsProcessingException("Error processing file " + originalAudioFilePath, e);
        }
    }
}
