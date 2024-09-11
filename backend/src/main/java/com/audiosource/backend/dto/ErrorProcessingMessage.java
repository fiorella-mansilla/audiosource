package com.audiosource.backend.dto;

import java.time.LocalDateTime;

public class ErrorProcessingMessage {
    private String correlationId;
    private String errorMessage;
    private LocalDateTime timestamp;
    private int retryCount;
    private ProcessingContext processingContext;

    public ErrorProcessingMessage(String correlationId, String errorMessage, LocalDateTime timestamp, int retryCount, ProcessingContext processingContext) {
        this.correlationId = correlationId;
        this.errorMessage = errorMessage;
        this.timestamp = timestamp;
        this.retryCount = retryCount;
        this.processingContext = processingContext;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public ProcessingContext getProcessingContext() {
        return processingContext;
    }

    public void setProcessingContext(ProcessingContext processingContext) {
        this.processingContext = processingContext;
    }

    @Override
    public String toString() {
        return "ErrorProcessingMessage {" +
                "correlationId='" + correlationId + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", timestamp=" + timestamp +
                ", retryCount=" + retryCount +
                ", processingContext=" + processingContext +
                '}';
    }
}
