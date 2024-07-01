package com.audiosource.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class DemucsService {

    private static final Logger logger = LoggerFactory.getLogger(DemucsService.class);

    private final String inputDirectory = "/Users/fiorellamansilla/demucs/originals/";
    private final String outputDirectory = "/Users/fiorellamansilla/demucs/";
    private Queue<File> audioFilesQueue = new ConcurrentLinkedQueue<>();

    /**
     * Constructor that initializes the service and populates the audioFilesQueue with files from the input directory.
     */
    public DemucsService() {
        scanDirectoryAndInitializeQueue();
    }

    /**
     * Processes the specified audio file using Demucs for music source separation.
     *
     * @param audioFilePath Absolute path of the audio file to process.
     * @throws IOException          If an I/O error occurs during processing.
     * @throws InterruptedException If the thread is interrupted while waiting for Demucs process completion.
     */
    public void processNextAudioFile(String audioFilePath) throws IOException, InterruptedException {

        try {
            File audioFile = new File(audioFilePath);

            // Validate if audio file exists
            if(!audioFile.exists()) {
                throw new IllegalArgumentException("Audio file not found : " + audioFilePath);
            }

            String pythonEnvPath;

            // Determine Python environment path based on file extension
            if (audioFilePath.endsWith(".mp3") || audioFilePath.endsWith(".wav")) {
                pythonEnvPath = "/Users/fiorellamansilla/miniconda3/envs/demucs-env/bin/python3";
                String[] commandArgs = { pythonEnvPath, "-m", "demucs", "-d", "cpu", audioFilePath };

                // Execute Demucs command via ProcessBuilder
                ProcessBuilder processBuilder = new ProcessBuilder(commandArgs);
                processBuilder.directory(new File(outputDirectory));
                processBuilder.inheritIO();

                Process process = processBuilder.start();
                int exitCode = process.waitFor();

                // Check Demucs process exit code
                if(exitCode != 0) {
                    throw new IOException("Demucs processing failed:" + audioFilePath);
                }
                logger.info("Successfully processed audio file {}", audioFilePath);
            } else {
                throw new IllegalArgumentException("Unsupported file format.");
            }
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            // Log error if processing fails and propagate the exception
            logger.error("Error processing file {}: {}", audioFilePath, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Scans the input directory for audio files and initializes the audioFilesQueue with them.
     */
    private void scanDirectoryAndInitializeQueue() {
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
     * Retrieves the path of the next audio file from the queue.
     *
     * @return Optional containing the absolute path of the next audio file, or empty if queue is empty.
     */
    public Optional<String> retrieveNextAudioFilePath() {
        File nextFile = audioFilesQueue.poll(); // Retrieves and removes the next file from the Queue
        return nextFile != null ? Optional.of(nextFile.getAbsolutePath()) : Optional.empty();
    }
}
