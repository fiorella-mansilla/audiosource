package com.audiosource.backend.dto;

public class ProcessedFileMessage {

    private String correlationId;
    private String processedAudioFilePath;

    public ProcessedFileMessage(String correlationId, String processedAudioFilePath) {
        this.correlationId = correlationId;
        this.processedAudioFilePath = processedAudioFilePath;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getProcessedAudioFilePath() {
        return processedAudioFilePath;
    }

    public void setProcessedAudioFilePath(String processedAudioFilePath) {
        this.processedAudioFilePath = processedAudioFilePath;
    }

    @Override
    public String toString() {
        return "ProcessedFileMessage {" +
                "correlationId='" + correlationId + '\'' +
                ", processedAudioFilePath='" + processedAudioFilePath + '\'' +
                '}';
    }
}
