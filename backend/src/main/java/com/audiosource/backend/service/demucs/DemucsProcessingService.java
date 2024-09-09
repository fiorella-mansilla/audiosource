package com.audiosource.backend.service.demucs;

import com.audiosource.backend.exception.DemucsProcessingException;
import com.audiosource.backend.util.S3Utils;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class DemucsProcessingService {

    private Queue<File> audioFilesQueue = new ConcurrentLinkedQueue<>();

    private static final Logger logger = LoggerFactory.getLogger(DemucsProcessingService.class);

    private final Dotenv dotenv;

    /**
     * Constructor that initializes the service and populates the audioFilesQueue with files from the input directory.
     */
    public DemucsProcessingService(Dotenv dotenv) {
        this.dotenv = dotenv;
        scanDirectoryAndInitializeQueue();
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

    /**
     * Scans the input directory for audio files and initializes the audioFilesQueue with them.
     *
     * This method searches the specified input directory for audio files in the .mp3 or .wav formats.
     * It adds all found files to the processing queue. Logs are generated to indicate the results of
     * the scan, such as the number of files added to the queue and any potential issues.
     */
    private void scanDirectoryAndInitializeQueue() {

        String inputDirectory = dotenv.get("DEMUCS_INPUT_DIRECTORY");

        File dir = new File(inputDirectory);
        if (dir.isDirectory() && dir.canRead()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".mp3") || name.endsWith(".wav"));
            if (files != null) {
                for (File file : files) {
                    audioFilesQueue.add(file);
                }
                logger.info("Initialized audio files queue with {} files from directory {}", files.length, inputDirectory);
            } else {
                logger.warn("No audio files found in directory {}", inputDirectory);
            }
        } else {
            logger.error("Input directory {} is not a valid directory or cannot be read", inputDirectory);
        }
    }

    /**
     * Fetches and removes the next audio file from the queue of files to be processed.
     * If the queue is empty, it returns an empty Optional.
     *
     * @return Optional containing the absolute path of the next audio file, or an empty Optional if
     *         the queue is empty.
     */
    public Optional<String> retrieveNextAudioFilePath() {
        File nextFile = audioFilesQueue.poll(); // Retrieves and removes the next file from the Queue
        return nextFile != null ? Optional.of(nextFile.getAbsolutePath()) : Optional.empty();
    }
}
