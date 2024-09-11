package com.audiosource.backend.dto;

/* This class bundles together the original audio file path and the AudioFileMessage from the AudioFilesQueue.*/
public class ProcessingContext {
    private final String originalAudioFilePath;
    private final AudioFileMessage audioFileMessage;

    public ProcessingContext(String originalAudioFilePath, AudioFileMessage audioFileMessage) {
        this.originalAudioFilePath = originalAudioFilePath;
        this.audioFileMessage = audioFileMessage;
    }

    public String getOriginalAudioFilePath() {
        return originalAudioFilePath;
    }

    public AudioFileMessage getAudioFileMessage() {
        return audioFileMessage;
    }
}
