package com.audiosource.backend.service;

import org.springframework.stereotype.Service;
import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class DemucsService {

    private final String inputDirectory = "/Users/fiorellamansilla/demucs/originals/";
    private final String outputDirectory = "/Users/fiorellamansilla/demucs/";
    private Queue<File> audioFilesQueue = new ConcurrentLinkedQueue<>();

    /* When initialized, the queue is immediately populated with audio files from the specified directory. */
    public DemucsService() {
        scanDirectoryAndInitializeQueue();
    }

    private void scanDirectoryAndInitializeQueue() {
        File dir = new File(inputDirectory);
        if (dir.isDirectory() && dir.canRead()) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".mp3") || name.endsWith(".wav"));
            if (files != null) {
                for (File file : files) {
                    audioFilesQueue.add(file);
                }
            }
        }
    }

    public Optional<String> retrieveNextAudioFilePath() {
        File nextFile = audioFilesQueue.poll(); // Retrieves and removes the next file from the Queue
        return nextFile != null ? Optional.of(nextFile.getAbsolutePath()) : Optional.empty();
    }

    /* Run Demucs in order to process the oldest audio waiting in the Files to be processed Queue. */
    public void processNextAudioFile(String audioFilePath) throws IOException, InterruptedException {

        try {
            File audioFile = new File(audioFilePath);

            if(!audioFile.exists()) {
                throw new IllegalArgumentException("Audio file not found : " + audioFilePath);
            }

            String pythonEnvPath;
            if (audioFilePath.endsWith(".mp3") || audioFilePath.endsWith(".wav")) {
                pythonEnvPath = "/Users/fiorellamansilla/miniconda3/envs/demucs-env/bin/python3";
                String[] commandArgs = { pythonEnvPath, "-m", "demucs", "-d", "cpu", audioFilePath };

                ProcessBuilder processBuilder = new ProcessBuilder(commandArgs);
                processBuilder.directory(new File(outputDirectory));
                processBuilder.inheritIO();

                Process process = processBuilder.start();
                int exitCode = process.waitFor();

                if(exitCode != 0) {
                    throw new IOException("Demucs processing failed:" + audioFilePath);
                }
            } else {
                throw new IllegalArgumentException("Unsupported file format.");
            }
        } catch (IOException | InterruptedException | IllegalArgumentException e) {
            System.err.println("Error processing file " + audioFilePath + ": " + e.getMessage());
            throw e; // Rethrow the exception to propagate it up the call stack
        }
    }
}
