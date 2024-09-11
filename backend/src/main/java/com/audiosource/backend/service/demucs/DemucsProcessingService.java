package com.audiosource.backend.service.demucs;

import com.audiosource.backend.dto.ProcessedFileMessage;
import com.audiosource.backend.enums.OutputFormat;
import com.audiosource.backend.enums.SeparationType;
import com.audiosource.backend.exception.DemucsProcessingException;
import com.audiosource.backend.messaging.producer.ProcessedFilesProducerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;

@Service
public class DemucsProcessingService {

    private final ProcessedFilesProducerService processedFilesProducerService;
    private static final Logger LOGGER = LoggerFactory.getLogger(DemucsProcessingService.class);

    @Value("${demucs.outputDirectory}")
    private String demucsOutputDirectory;

    @Value("${python.env.path}")
    private String pythonEnvPath;

    @Autowired
    public DemucsProcessingService(ProcessedFilesProducerService processedFilesProducerService) {
        this.processedFilesProducerService = processedFilesProducerService;
    }

    /**
     * Processes the downloaded audio file using the Demucs AI model for music source separation.
     *
     * @param originalAudioFilePath The absolute path of the audio file to process.
     * @param separationType The type of separation to perform (vocal remover or stems splitter).
     * @param outputFormat The format of the output audio files (mp3 or wav).
     * @throws DemucsProcessingException If an I/O error occurs or the process fails.
     */
    public void processRetrievedAudioFile(String correlationId, String originalAudioFilePath, SeparationType separationType, OutputFormat outputFormat) throws DemucsProcessingException {
        // Ensure the service is ready for processing
        if (!isReadyForProcessing()) {
            throw new DemucsProcessingException("Service is not ready for processing. Check environment and output directory.");
        }

        validateAudioFile(originalAudioFilePath);

        // Construct the Demucs processing command arguments
        String[] commandArgs = constructCommandArgs(separationType, outputFormat, originalAudioFilePath);

        try {
            executeCommand(commandArgs);

            // Extract the original file name and create the processed file path
            File originalFile = new File(originalAudioFilePath);
            String originalFileName = originalFile.getName();
            String originalFileNameWithoutExtension = originalFileName.substring(0, originalFileName.lastIndexOf('.'));

            String processedAudioFilePath = demucsOutputDirectory + File.separator + "htdemucs"
                    + File.separator + originalFileNameWithoutExtension;

            // Create a new ProcessedFileMessage object DTO
            ProcessedFileMessage processedFileMessage = new ProcessedFileMessage(correlationId, processedAudioFilePath);

            // Publish the message to ProcessedFilesQueue (RabbitMQ)
            processedFilesProducerService.publishProcessedFileNotification(processedFileMessage);

            LOGGER.info("Successfully processed audio file by DemucsProcessingService {}", processedAudioFilePath);

        } catch (IOException | InterruptedException e) {
            throw new DemucsProcessingException("Error processing file " + originalAudioFilePath, e);
        }
    }

    // Validate the existence of the audio file to be processed
    private void validateAudioFile(String originalAudioFilePath) {
        File originalAudioFile = new File(originalAudioFilePath);
        if (!originalAudioFile.exists()) {
            throw new IllegalArgumentException("Audio file not found: " + originalAudioFilePath);
        }
    }

    // Construct the command arguments based on the separation type and output format arguments
    private String[] constructCommandArgs(SeparationType separationType, OutputFormat outputFormat, String originalAudioFilePath) {
        if (separationType == SeparationType.VOCAL_REMOVER && outputFormat == OutputFormat.MP3) {
            return constructVocalsMp3CommandArgs(originalAudioFilePath);
        } else if (separationType == SeparationType.VOCAL_REMOVER) {
            return constructVocalsCommandArgs(originalAudioFilePath);
        } else if (outputFormat == OutputFormat.MP3) {
            return constructMp3CommandArgs(originalAudioFilePath);
        } else {
            return constructDefaultCommandArgs(originalAudioFilePath);
        }
    }

    // Command arguments for the default Demucs processing (4-stems splitter and WAV output format)
    private String[] constructDefaultCommandArgs(String originalAudioFilePath) {
        return new String[]{ pythonEnvPath, "-m", "demucs", "-d", "cpu", originalAudioFilePath };
    }

    // Command arguments for vocal remover processing (2-stems splitter and WAV output format)
    private String[] constructVocalsCommandArgs(String originalAudioFilePath) {
        return new String[] { pythonEnvPath, "-m", "demucs", "--two-stems=vocals", "cpu", originalAudioFilePath };
    }

    // Command arguments for MP3 output format (4-stems splitter and MP3 output format)
    private String[] constructMp3CommandArgs(String originalAudioFilePath) {
        return new String[] { pythonEnvPath, "-m", "demucs", "--mp3", "cpu", originalAudioFilePath };
    }

    // Command arguments for vocal remover processing with MP3 output format (2-stems splitter and MP3 output format)
    private String[] constructVocalsMp3CommandArgs(String originalAudioFilePath) {
        return new String[]{ pythonEnvPath, "-m", "demucs", "--two-stems=vocals", "--mp3", "cpu", originalAudioFilePath };
    }

    // Execute the command to process the retrieved audio file using Demucs
    private void executeCommand(String[] commandArgs) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(commandArgs);
        processBuilder.directory(new File(demucsOutputDirectory));
        processBuilder.inheritIO();

        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if(exitCode != 0) {
            throw new IOException("Demucs processing failed for command: " + String.join(" ", commandArgs));
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
            LOGGER.error("Python environment for Demucs is not available or executable.");
            return false;
        }

        // Check if the Demucs output directory is available and writable
        File outputDirectory = new File(demucsOutputDirectory);
        if (!outputDirectory.exists() || !outputDirectory.canWrite()) {
            LOGGER.error("Demucs output directory is not available or writable.");
            return false;
        }

        // If both checks pass, the service is ready for processing
        return true;
    }
}
