package com.audiosource.backend.dto;

import com.audiosource.backend.enums.OutputFormat;
import com.audiosource.backend.enums.SeparationType;

/* This class has all the necessary metadata to be consumed by the S3DownloadService & the DemucsProcessingService*/
public class AudioFileMessage {
    private String correlationId;
    private String keyName;
    private long fileSize;
    private SeparationType separationType;
    private OutputFormat outputFormat;

    public AudioFileMessage(String correlationId, String keyName, long fileSize, SeparationType separationType, OutputFormat outputFormat) {
        this.correlationId = correlationId;
        this.keyName = keyName;
        this.fileSize = fileSize;
        this.separationType = separationType;
        this.outputFormat = outputFormat;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public SeparationType getSeparationType() {
        return separationType;
    }

    public void setSeparationType(SeparationType separationType) {
        this.separationType = separationType;
    }

    public OutputFormat getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(OutputFormat outputFormat) {
        this.outputFormat = outputFormat;
    }

    @Override
    public String toString() {
        return "AudioFileMessage{" +
                "correlationId='" + correlationId + '\'' +
                ", keyName='" + keyName + '\'' +
                ", fileSize=" + fileSize +
                ", separationType='" + separationType + '\'' +
                ", outputFormat='" + outputFormat + '\'' +
                '}';
    }
}
